alter table "order" 
add column if not exists id_table integer,
add column if not exists installation_datetime timestamp without time zone,
add column if not exists departure_datetime timestamp without time zone;

alter table "order" 
add constraint fk_order_table 
foreign key (id_table) references restaurant_table(id) on delete set null;

create index if not exists idx_order_table_id on "order"(id_table);
create index if not exists idx_order_installation_datetime on "order"(installation_datetime);
create index if not exists idx_order_departure_datetime on "order"(departure_datetime);
