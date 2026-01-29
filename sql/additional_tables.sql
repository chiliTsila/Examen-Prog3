-- Table pour les plats/dishes
create table if not exists dish (
    id serial primary key,
    name varchar(255) not null,
    description text,
    price decimal(10, 2) not null,
    category varchar(50),
    is_available boolean default true,
    created_at timestamp without time zone default current_timestamp
);

create index if not exists idx_dish_name on dish(name);
create index if not exists idx_dish_category on dish(category);

-- Table pour les ingrédients
create table if not exists ingredient (
    id serial primary key,
    name varchar(255) not null unique,
    description text,
    quantity_in_stock decimal(10, 2),
    unit varchar(50),
    created_at timestamp without time zone default current_timestamp
);

create index if not exists idx_ingredient_name on ingredient(name);

-- Table pour les détails de la commande (liaison entre order et dish)
create table if not exists order_item (
    id serial primary key,
    id_order integer not null,
    id_dish integer not null,
    quantity integer not null,
    unit_price decimal(10, 2) not null,
    notes text,
    created_at timestamp without time zone default current_timestamp,
    constraint fk_order_item_order foreign key (id_order) references "order"(id) on delete cascade,
    constraint fk_order_item_dish foreign key (id_dish) references dish(id) on delete restrict
);

create index if not exists idx_order_item_order_id on order_item(id_order);
create index if not exists idx_order_item_dish_id on order_item(id_dish);

-- Table de liaison entre dishes et ingredients
create table if not exists dish_ingredient (
    id serial primary key,
    id_dish integer not null,
    id_ingredient integer not null,
    quantity decimal(10, 2) not null,
    unit varchar(50),
    constraint fk_dish_ingredient_dish foreign key (id_dish) references dish(id) on delete cascade,
    constraint fk_dish_ingredient_ingredient foreign key (id_ingredient) references ingredient(id) on delete cascade,
    constraint uk_dish_ingredient unique(id_dish, id_ingredient)
);

create index if not exists idx_dish_ingredient_dish_id on dish_ingredient(id_dish);
create index if not exists idx_dish_ingredient_ingredient_id on dish_ingredient(id_ingredient);

-- Table pour les clients/guests
create table if not exists customer (
    id serial primary key,
    first_name varchar(100) not null,
    last_name varchar(100) not null,
    email varchar(255),
    phone varchar(20),
    created_at timestamp without time zone default current_timestamp
);

create index if not exists idx_customer_email on customer(email);
create index if not exists idx_customer_phone on customer(phone);

-- Table pour les employés/staff
create table if not exists staff (
    id serial primary key,
    first_name varchar(100) not null,
    last_name varchar(100) not null,
    email varchar(255) unique,
    phone varchar(20),
    position varchar(100),
    hire_date date,
    is_active boolean default true,
    created_at timestamp without time zone default current_timestamp
);

create index if not exists idx_staff_email on staff(email);
create index if not exists idx_staff_position on staff(position);

-- Optionnel: Table pour les paiements
create table if not exists payment (
    id serial primary key,
    id_order integer not null,
    amount decimal(10, 2) not null,
    payment_method varchar(50),
    payment_date timestamp without time zone default current_timestamp,
    status varchar(50) default 'completed',
    constraint fk_payment_order foreign key (id_order) references "order"(id) on delete cascade
);

create index if not exists idx_payment_order_id on payment(id_order);
create index if not exists idx_payment_status on payment(status);
