create table if not exists restaurant_table (
    id serial primary key,
    number integer unique not null,
    capacity integer not null
);

create index if not exists idx_table_number on restaurant_table(number);
