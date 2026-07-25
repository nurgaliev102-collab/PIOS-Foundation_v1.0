-- Sprint 7B: Personal Network Flow MVP. Adds an optional, plain-text
-- display name so a passenger invited through a driver's own link can be
-- shown who invited them ("Вас пригласил Артур") -- mirrors
-- V6__add_optional_order_destination.sql in order-management: a plain
-- nullable column, no new domain concept, no default.
ALTER TABLE drivers ADD COLUMN display_name TEXT NULL;
