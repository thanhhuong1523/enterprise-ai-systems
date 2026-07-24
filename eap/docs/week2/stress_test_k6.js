import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Counter, Rate } from 'k6/metrics';

/* ============================================================
 * CONFIG
 * ============================================================ */

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

const USER_A = {
    username: __ENV.USERNAME_A || 'user1',
    password: __ENV.PASSWORD_A || '123456'
};

const USER_B = {
    username: __ENV.USERNAME_B || 'user2',
    password: __ENV.PASSWORD_B || '123456'
};

/*
 * 2 file khác nhau
 * concurrent.pdf dùng test Advisory Lock
 * soft-delete.pdf dùng test Reupload
 */

const concurrentPdf = open('./concurrent.pdf', 'b');
const softDeletePdf = open('./soft_delete.pdf', 'b');


/* ============================================================
 * METRICS
 * ============================================================ */

export const concurrentCreated = new Counter('concurrent_created');

export const concurrentConflict = new Counter('concurrent_conflict');


/* ============================================================
 * OPTIONS
 * ============================================================ */

export const options = {

  thresholds:{

    concurrent_created:[
    'count==1'
    ],
    
    concurrent_conflict:[
    'count==99'
    ],
    
    },

    scenarios: {

        concurrent_duplicate_upload: {

            executor: 'per-vu-iterations',

            vus: 100,

            iterations: 1,

            maxDuration: '60s',

            exec: 'testConcurrentUpload'
        },

        cross_department_upload: {

            executor: 'per-vu-iterations',

            vus: 1,

            iterations: 1,

            startTime: '70s',

            exec: 'testCrossDepartment'
        },

        reupload_after_soft_delete: {

            executor: 'per-vu-iterations',

            vus: 1,

            iterations: 1,

            startTime: '90s',

            exec: 'testSoftDeleteReupload'
        }
    }
};


/* ============================================================
 * COMMON FUNCTIONS
 * ============================================================ */

function extractData(res) {

    const body = res.json();

    return body.data ?? body;
}


function login(username, password) {

    const res = http.post(

        `${BASE_URL}/api/v1/auth/login`,

        JSON.stringify({
            username,
            password
        }),

        {
            headers: {
                'Content-Type': 'application/json'
            }
        }
    );

    check(res, {

        'Login success': r => r.status === 200

    });

    if (res.status !== 200) {

        throw new Error(`Login failed: ${username}`);
    }

    const body = res.json();

    return body.data.accessToken;
}


function upload(token, title, fileData, fileName) {

    const payload = {

        title,

        file: http.file(
            fileData,
            fileName,
            'application/pdf'
        )
    };

    return http.post(

        `${BASE_URL}/api/v1/original-documents`,

        payload,

        {
            headers: {
                Authorization: `Bearer ${token}`
            }
        }
    );
}


function deleteDocument(token, id) {

    return http.del(

        `${BASE_URL}/api/v1/original-documents/${id}`,

        null,

        {
            headers: {
                Authorization: `Bearer ${token}`
            }
        }
    );
}

/* ============================================================
 * SETUP
 * ============================================================ */

export function setup() {

  console.log('========== LOGIN ==========');

  const tokenA = login(
      USER_A.username,
      USER_A.password
  );

  const tokenB = login(
      USER_B.username,
      USER_B.password
  );

  console.log('Login completed');
  console.log('Prepare soft delete document');

  const uploadRes = upload(

      tokenB,

      'Soft Delete Test Document',

      softDeletePdf,

      'soft_delete.pdf'
  );

  check(uploadRes, {

      'Prepare soft delete success':

          r => r.status === 201

  });

  if (uploadRes.status !== 201) {

    console.log("STATUS =", uploadRes.status);
    console.log("BODY =", uploadRes.body);

    throw new Error(
        `Cannot prepare soft delete document: ${uploadRes.status}`
    );
}

  const document = extractData(uploadRes);

  console.log(
      `Soft delete document id = ${document.id}`
  );


  /*
   * Trả token + id
   */

  return {

      tokenA,

      tokenB,

      softDeleteDocumentId: document.id
  };
}

/* ============================================================
 * UPDATE METRICS
 * ============================================================ */

function updateConcurrentMetrics(res) {

  if (res.status === 201) {
      concurrentCreated.add(1);
  }
  else if (res.status === 409) {
      concurrentConflict.add(1);
  }

}

/* ============================================================
 * SCENARIO 1
 *
 * Concurrent Upload
 *
 * 100 request upload cùng 1 file
 *
 * Kỳ vọng:
 *
 * 1 request -> 201
 *
 * 99 request -> 409
 *
 * ============================================================ */

export function testConcurrentUpload(data) {

  sleep(Math.random() * 0.2);

  const response = upload(

      data.tokenA,

      'Concurrent Upload Test',

      concurrentPdf,

      'concurrent.pdf'
  );

  updateConcurrentMetrics(response);

  check(response, {

      /*
       * Chỉ được phép trả về
       *
       * 201
       *
       * hoặc
       *
       * 409
       */
      '[Concurrent] HTTP status': r =>

          r.status === 201 ||

          r.status === 409,


      /*
       * Không được có lỗi hệ thống
       */
      '[Concurrent] No HTTP 500': r =>

          r.status !== 500,


      /*
       * Không timeout
       */
      '[Concurrent] No timeout': r =>

          r.status !== 408 &&

          r.status !== 504,


      /*
       * Không bị connection starvation
       */
      '[Concurrent] No 429': r =>

          r.status !== 429

  });


  /*
   * Nếu upload thành công
   */

  if (response.status === 201) {

      const document = extractData(response);

      check(document, {

          '[Concurrent] id exists':

              d => d.id != null,

          '[Concurrent] businessCode exists':

              d => d.businessCode != null,

          '[Concurrent] hash exists':

              d => d.hash != null

      });

  }
  sleep(1);
}

/* ============================================================
 * SCENARIO 2
 *
 * Cross Department
 *
 * Department A đã upload trước.
 *
 * Department B upload cùng file.
 *
 * Kỳ vọng:
 *
 * 201
 *
 * Metadata mới
 *
 * Reuse physical file
 * ============================================================ */

export function testCrossDepartment(data) {

  group('Cross Department Upload', () => {

      const response = upload(

          data.tokenB,

          'Cross Department Upload',

          concurrentPdf,

          'concurrent.pdf'
      );

      check(response, {

          '[Cross] HTTP 201':

              r => r.status === 201,

          '[Cross] Không lỗi 500':

              r => r.status !== 500,

          '[Cross] Không timeout':

              r =>

                  r.status !== 408 &&

                  r.status !== 504

      });


      if (response.status !== 201) {

          return;
      }

      const document = extractData(response);

      check(document, {

          '[Cross] id exists':

              d => d.id != null,

          '[Cross] businessCode exists':

              d => d.businessCode != null,

          '[Cross] hash exists':

              d => d.hash != null

      });

      /*
       * Nếu backend trả ownerDepartmentId
       */

      if (document.ownerDepartmentId !== undefined) {

          check(document, {

              '[Cross] ownerDepartmentId exists':

                  d => d.ownerDepartmentId != null

          });
      }
  });
  sleep(1);
}

/* ============================================================
 * SCENARIO 3
 *
 * Upload
 * ↓
 * Soft Delete
 * ↓
 * Upload lại
 *
 * Kỳ vọng:
 *
 * 201 Created
 *
 * Metadata mới
 *
 * Reuse physical file
 * ============================================================ */

export function testSoftDeleteReupload(data) {

  group('Soft Delete Reupload', () => {

      /*
       * [SỬA ĐỔI] Sử dụng token của Trưởng phòng (data.tokenManager) để xóa mềm document đã tạo trong setup()
       */
      const deleteRes = deleteDocument(
          data.tokenB, // Thay data.tokenA thành token của Trưởng phòng
          data.softDeleteDocumentId
      );

      check(deleteRes, {
          '[SoftDelete] Delete success':
              r =>
                  r.status === 204 ||
                  r.status === 200
      });

      /*
       * Chờ transaction commit và DB Flush hoàn toàn
       */
      sleep(1);


      /*
       * [SỬA ĐỔI] Upload lại đúng file cũ (Có thể dùng token Trưởng phòng hoặc tokenA tùy quy trình upload)
       */
      const uploadRes = upload(
          data.tokenB, // Đảm bảo đồng bộ quyền thao tác trên tệp tin vừa xóa
          'Soft Delete Reupload',
          softDeletePdf,
          'soft-delete.pdf'
      );

      check(uploadRes, {
          '[SoftDelete] HTTP 201':
              r => r.status === 201,
          '[SoftDelete] Không lỗi 500':
              r => r.status !== 500
      });

      if (uploadRes.status !== 201) {
          // Gợi ý Debug: In log nếu bước tải lên lại thất bại do lỗi chặn trùng ở Backend
          // console.log(`[DEBUG] Reupload failed status: ${uploadRes.status}, Body: ${uploadRes.body}`);
          return;
      }

      const document = extractData(uploadRes);


      /*
       * Metadata mới
       */
      check(document, {
          '[SoftDelete] Data structure valid': d => d !== null && typeof d === 'object',
          '[SoftDelete] id exists':
              d => d && d.id != null,
          '[SoftDelete] businessCode exists':
              d => d && d.businessCode != null,
          '[SoftDelete] hash exists':
              d => d && d.hash != null
      });


      /*
       * ID phải khác metadata cũ
       */
      check(document, {
          '[SoftDelete] New metadata':
              d => d && d.id !== data.softDeleteDocumentId
      });


      /*
       * Nếu backend expose reusePhysicalFile
       */
      if (document && document.reusePhysicalFile !== undefined) {
          check(document, {
              '[SoftDelete] Reuse physical file':
                  d => d.reusePhysicalFile === true
          });
      }

  });

  sleep(1);
}
