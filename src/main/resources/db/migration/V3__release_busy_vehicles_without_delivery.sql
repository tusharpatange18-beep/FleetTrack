-- Data repair. Older versions let users set status = BUSY by hand, which left vehicles BUSY
-- with no delivery holding them; such a vehicle could never be assigned again.
-- A vehicle is BUSY only while a delivery in ASSIGNED, PICKED_UP or IN_TRANSIT references it.
UPDATE vehicle SET status = 'AVAILABLE'
WHERE status = 'BUSY'
  AND NOT EXISTS (
      SELECT 1 FROM delivery d
      WHERE d.vehicle_id = vehicle.id
        AND d.status IN ('ASSIGNED', 'PICKED_UP', 'IN_TRANSIT'));
