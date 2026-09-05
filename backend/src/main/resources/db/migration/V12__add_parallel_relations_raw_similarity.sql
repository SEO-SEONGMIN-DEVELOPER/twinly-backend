ALTER TABLE parallel_relations
    ADD COLUMN raw_similarity DOUBLE NULL AFTER similarity;

UPDATE parallel_relations
SET raw_similarity = similarity / 100.0
WHERE raw_similarity IS NULL;

UPDATE parallel_relations pr
    JOIN (SELECT id,
                 p,
                 CASE
                     WHEN p < 0.10 THEN (p - 0.00) / 0.10
                     WHEN p < 0.29 THEN (p - 0.10) / 0.19
                     WHEN p < 0.51 THEN (p - 0.29) / 0.22
                     WHEN p < 0.90 THEN (p - 0.51) / 0.39
                     ELSE (p - 0.90) / 0.10
                     END AS t
          FROM (SELECT id, 1 / (1 + EXP(-1.702 * (raw_similarity - 0.457) / 0.084)) AS p
                FROM parallel_relations
                WHERE raw_similarity IS NOT NULL) percentile) q ON q.id = pr.id
SET pr.similarity = LEAST(99, GREATEST(10, ROUND(
        CASE
            WHEN q.p < 0.10 THEN 10 + 20.000000 * q.t + 5.749386 * POW(q.t, 2) - 5.749386 * POW(q.t, 3)
            WHEN q.p < 0.29 THEN 30 + 27.076167 * q.t - 12.722060 * POW(q.t, 2) + 5.645893 * POW(q.t, 3)
            WHEN q.p < 0.51 THEN 50 + 21.501788 * q.t + 4.639150 * POW(q.t, 2) - 6.140938 * POW(q.t, 3)
            WHEN q.p < 0.90 THEN 70 + 21.906077 * q.t - 25.322262 * POW(q.t, 2) + 18.416184 * POW(q.t, 3)
            ELSE 85 + 6.797463 * q.t + 14.405073 * POW(q.t, 2) - 7.202537 * POW(q.t, 3)
            END)));

UPDATE parallel_relations
SET relation = CASE
                   WHEN similarity >= 85 THEN 'BEST_FRIEND'
                   WHEN similarity >= 70 THEN 'CLOSE'
                   WHEN similarity >= 50 THEN 'AWKWARD'
                   WHEN similarity >= 30 THEN 'STRANGER'
                   ELSE 'ENEMY'
    END
WHERE raw_similarity IS NOT NULL;
