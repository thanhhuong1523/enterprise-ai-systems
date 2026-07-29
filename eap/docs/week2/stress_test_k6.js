import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';

/* ============================================================
 * CONFIG
 * ============================================================ */

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

const USER_A = {
    username: __ENV.USERNAME_A || 'user2',
    password: __ENV.PASSWORD_A || '123456'
};

/*
 * File dùng test Advisory Lock / Concurrent Upload
 */
const concurrentPdf = open('./concurrent.pdf', 'b');


/* ============================================================
 * METRICS
 * ============================================================ */

export const concurrentCreated = new Counter('concurrent_created');
export const concurrentConflict = new Counter('concurrent_conflict');
export const concurrentError500 = new Counter('concurrent_error_500');
export const concurrentError429 = new Counter('concurrent_error_429');
export const concurrentErrorOther = new Counter('concurrent_error_other');


/* ============================================================
 * OPTIONS
 * ============================================================ */

export const options = {
  thresholds: {
    concurrent_created: [
      'count==1'
    ],
    concurrent_conflict: [
      'count==99'
    ],
    // Chấp nhận tỷ lệ lỗi http_req_failed là 99% (99/100 requests trả về 409 Conflict theo thiết kế)
    http_req_failed: [
      'rate < 0.995'
    ]
  },
  scenarios: {
    concurrent_duplicate_upload: {
      executor: 'per-vu-iterations',
      vus: 100,
      iterations: 1,
      maxDuration: '60s',
      exec: 'testConcurrentUpload'
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


/* ============================================================
 * SETUP
 * ============================================================ */

export function setup() {
  console.log('========== LOGIN ==========');
  const tokenA = login(
      USER_A.username,
      USER_A.password
  );
  console.log('Login completed');

  return {
      tokenA
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
  else if (res.status === 500) {
      concurrentError500.add(1);
  }
  else if (res.status === 429) {
      concurrentError429.add(1);
  }
  else {
      concurrentErrorOther.add(1);
  }
}


/* ============================================================
 * SCENARIO 1: Concurrent Upload
 *
 * 100 requests upload cùng 1 file đồng thời.
 *
 * Kỳ vọng:
 * - 1 request -> 201 Created (Tạo metadata thành công, ghi file vật lý)
 * - 99 requests -> 409 Conflict (Duplicate Document trong cùng phòng ban)
 * ============================================================ */

export function testConcurrentUpload(data) {
  // Không dùng sleep để tạo ra sự tranh chấp và đồng thời tối đa (contention) khi bắn 100 requests cùng lúc.

  const response = upload(
      data.tokenA,
      'Concurrent Upload Test',
      concurrentPdf,
      'concurrent.pdf'
  );

  updateConcurrentMetrics(response);

  check(response, {
      /*
       * Chỉ được phép trả về 201 (Created) hoặc 409 (Conflict - Trùng lặp nghiệp vụ)
       */
      '[Concurrent] HTTP status is 201 or 409': r =>
          r.status === 201 || r.status === 409,

      /*
       * Không được có lỗi hệ thống (HTTP 500)
       */
      '[Concurrent] No HTTP 500': r =>
          r.status !== 500,

      /*
       * Không timeout
       */
      '[Concurrent] No timeout': r =>
          r.status !== 408 && r.status !== 504,

      /*
       * Không bị connection starvation
       */
      '[Concurrent] No 429': r =>
          r.status !== 429,

      /*
       * Kiểm chứng cấu trúc Response Body của lỗi 409 Conflict (ERR_DUPLICATE_DOCUMENT)
       * Theo Detailed Design Section 3.2
       */
      '[Concurrent] HTTP 409 Body is valid': r => {
          if (r.status !== 409) return true;
          try {
              const body = r.json();
              return body.success === false &&
                     body.data === null &&
                     body.error !== null &&
                     body.error.errorCode === 'ERR_DUPLICATE_DOCUMENT';
          } catch (e) {
              return false;
          }
      }
  });

  /*
   * Nếu upload thành công
   */
  if (response.status === 201) {
      const document = extractData(response);
      check(document, {
          '[Concurrent] id exists':
              d => d && d.id != null,
          '[Concurrent] businessCode exists':
              d => d && d.businessCode != null,
          '[Concurrent] hash exists':
              d => d && d.hash != null
      });
  }
  
  sleep(1);
}
