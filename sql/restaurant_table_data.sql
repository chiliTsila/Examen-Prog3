insert into restaurant_table (id, number, capacity) values
(1, 1, 4),
(2, 2, 2),
(3, 3, 6),
(4, 4, 2),
(5, 5, 8);

select setval('restaurant_table_id_seq', (select max(id) from restaurant_table));
