-- Sync sequence to prevent business code collisions with existing records
-- Uses regex filter to only cast numeric business codes, avoiding hexadecimal ones from Week 1 (e.g. ORIG_0A894C8C)
SELECT setval('doc_business_code_seq', COALESCE((
    SELECT MAX(CAST(substring(business_code from 6) AS INTEGER)) 
    FROM documents 
    WHERE business_code ~ '^ORIG_[0-9]+$'
), 100000));
