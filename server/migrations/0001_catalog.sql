-- Bounded shared metadata only: no user lists, notes, ratings or activity logs.
CREATE TABLE IF NOT EXISTS catalog_budget (
  id INTEGER PRIMARY KEY CHECK(id=1),
  entity_count INTEGER NOT NULL DEFAULT 0,
  payload_bytes INTEGER NOT NULL DEFAULT 0,
  max_entities INTEGER NOT NULL DEFAULT 10000,
  max_payload_bytes INTEGER NOT NULL DEFAULT 209715200,
  write_day TEXT NOT NULL DEFAULT '',
  entity_writes_today INTEGER NOT NULL DEFAULT 0,
  max_entity_writes_per_day INTEGER NOT NULL DEFAULT 500
);
INSERT OR IGNORE INTO catalog_budget(id) VALUES(1);
CREATE TABLE IF NOT EXISTS catalog_lock (
  id INTEGER PRIMARY KEY CHECK(id=1), token TEXT NOT NULL, expires_at INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS catalog_entities (
  id TEXT PRIMARY KEY,
  category TEXT NOT NULL,
  payload TEXT NOT NULL,
  payload_bytes INTEGER NOT NULL CHECK(payload_bytes BETWEEN 1 AND 32768),
  revision INTEGER NOT NULL DEFAULT 1,
  updated_at INTEGER NOT NULL,
  feature_mask INTEGER NOT NULL DEFAULT 0
);
CREATE TABLE IF NOT EXISTS catalog_refs (
  ref_key TEXT PRIMARY KEY,
  entity_id TEXT NOT NULL REFERENCES catalog_entities(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS catalog_refs_entity ON catalog_refs(entity_id);
CREATE TABLE IF NOT EXISTS catalog_titles (
  category TEXT NOT NULL,
  title_key TEXT NOT NULL,
  entity_id TEXT NOT NULL REFERENCES catalog_entities(id) ON DELETE CASCADE,
  PRIMARY KEY(category,title_key,entity_id)
);
CREATE TABLE IF NOT EXISTS catalog_categories (
  category TEXT PRIMARY KEY,
  entities INTEGER NOT NULL DEFAULT 0,
  genres INTEGER NOT NULL DEFAULT 0,
  ratings INTEGER NOT NULL DEFAULT 0,
  rating_counts INTEGER NOT NULL DEFAULT 0,
  creators INTEGER NOT NULL DEFAULT 0,
  languages INTEGER NOT NULL DEFAULT 0,
  descriptions INTEGER NOT NULL DEFAULT 0
);
CREATE TRIGGER IF NOT EXISTS catalog_insert_guard BEFORE INSERT ON catalog_entities BEGIN
  SELECT CASE WHEN (SELECT entity_count >= max_entities OR payload_bytes + NEW.payload_bytes > max_payload_bytes OR
    (write_day = date('now') AND entity_writes_today >= max_entity_writes_per_day) FROM catalog_budget WHERE id=1)
    THEN RAISE(ABORT,'catalog budget exceeded') END;
END;
CREATE TRIGGER IF NOT EXISTS catalog_update_guard BEFORE UPDATE ON catalog_entities BEGIN
  SELECT CASE WHEN (SELECT payload_bytes - OLD.payload_bytes + NEW.payload_bytes > max_payload_bytes OR
    (write_day = date('now') AND entity_writes_today >= max_entity_writes_per_day) FROM catalog_budget WHERE id=1)
    THEN RAISE(ABORT,'catalog budget exceeded') END;
END;
CREATE TRIGGER IF NOT EXISTS catalog_insert_stats AFTER INSERT ON catalog_entities BEGIN
  UPDATE catalog_budget SET entity_count=entity_count+1, payload_bytes=payload_bytes+NEW.payload_bytes,
    entity_writes_today=CASE WHEN write_day=date('now') THEN entity_writes_today+1 ELSE 1 END, write_day=date('now') WHERE id=1;
  INSERT INTO catalog_categories(category,entities,genres,ratings,rating_counts,creators,languages,descriptions)
    VALUES(NEW.category,1,(NEW.feature_mask&1)!=0,(NEW.feature_mask&2)!=0,(NEW.feature_mask&4)!=0,
      (NEW.feature_mask&8)!=0,(NEW.feature_mask&16)!=0,(NEW.feature_mask&32)!=0)
    ON CONFLICT(category) DO UPDATE SET entities=entities+1,genres=genres+excluded.genres,ratings=ratings+excluded.ratings,
      rating_counts=rating_counts+excluded.rating_counts,creators=creators+excluded.creators,
      languages=languages+excluded.languages,descriptions=descriptions+excluded.descriptions;
END;
CREATE TRIGGER IF NOT EXISTS catalog_update_stats AFTER UPDATE ON catalog_entities BEGIN
  UPDATE catalog_budget SET payload_bytes=payload_bytes-OLD.payload_bytes+NEW.payload_bytes,
    entity_writes_today=CASE WHEN write_day=date('now') THEN entity_writes_today+1 ELSE 1 END, write_day=date('now') WHERE id=1;
  UPDATE catalog_categories SET
    genres=genres-((OLD.feature_mask&1)!=0)+((NEW.feature_mask&1)!=0),
    ratings=ratings-((OLD.feature_mask&2)!=0)+((NEW.feature_mask&2)!=0),
    rating_counts=rating_counts-((OLD.feature_mask&4)!=0)+((NEW.feature_mask&4)!=0),
    creators=creators-((OLD.feature_mask&8)!=0)+((NEW.feature_mask&8)!=0),
    languages=languages-((OLD.feature_mask&16)!=0)+((NEW.feature_mask&16)!=0),
    descriptions=descriptions-((OLD.feature_mask&32)!=0)+((NEW.feature_mask&32)!=0) WHERE category=NEW.category;
END;
-- Empty except during a batch. CHECK failure rolls back that entire batch.
CREATE TABLE IF NOT EXISTS catalog_lease_guard(valid INTEGER NOT NULL CHECK(valid=1));
