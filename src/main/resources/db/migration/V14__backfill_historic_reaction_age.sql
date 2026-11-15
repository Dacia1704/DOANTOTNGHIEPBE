-- Old reactions received the deployment timestamp in V13. Use target age as a conservative estimate.
-- Preserve reactions made after the V13 deployment window.
UPDATE community_reactions r
 LEFT JOIN community_posts p ON r.target_type='POST' AND p.id=r.target_id
 LEFT JOIN community_comments c ON r.target_type='POST_COMMENT' AND c.id=r.target_id
 SET r.created_at=COALESCE(p.created_at,c.created_at,r.created_at)
 WHERE r.created_at < COALESCE(
   (SELECT DATE_ADD(installed_on,INTERVAL 1 SECOND) FROM flyway_schema_history WHERE version='13' AND success=1),
   CURRENT_TIMESTAMP(6));
