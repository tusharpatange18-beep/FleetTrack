-- Each delivery carries its own route, so one customer can have trips with different addresses.
ALTER TABLE delivery ADD COLUMN pickup_latitude DOUBLE;
ALTER TABLE delivery ADD COLUMN pickup_longitude DOUBLE;
ALTER TABLE delivery ADD COLUMN drop_latitude DOUBLE;
ALTER TABLE delivery ADD COLUMN drop_longitude DOUBLE;

-- Backfill existing deliveries from their customer's addresses.
-- Correlated subqueries (not UPDATE ... JOIN) so the same script runs on MySQL and on H2 in tests.
UPDATE delivery SET
    pickup_latitude  = (SELECT c.pickup_latitude  FROM customer c WHERE c.id = delivery.customer_id),
    pickup_longitude = (SELECT c.pickup_longitude FROM customer c WHERE c.id = delivery.customer_id),
    drop_latitude    = (SELECT c.drop_latitude    FROM customer c WHERE c.id = delivery.customer_id),
    drop_longitude   = (SELECT c.drop_longitude   FROM customer c WHERE c.id = delivery.customer_id);

-- Proximity search filters on status and then a latitude range (the bounding-box prefilter).
-- A composite index serves both; its leftmost column also covers status-only lookups.
CREATE INDEX idx_vehicle_status_lat ON vehicle(status, current_latitude);
