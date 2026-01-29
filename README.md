# PROG3 Examen - Table Management System

## Fonctionnalité

Gestion des contraintes d'espace dans un restaurant :
- Modèle `Table` pour représenter les tables du restaurant
- Validation de disponibilité des tables lors de la création d'une commande
- Messages d'erreur informatifs listant les tables disponibles

## Structure

```
src/main/java/com/jdbctd2/
  ├── model/
  │   └── Table.java
  └── repository/
      └── interf/
          └── TableRepository.java
          
sql/
  ├── restaurant_table_schema.sql
  ├── alter_order_schema.sql
  └── restaurant_table_data.sql
```

## Compilation

```bash
mvn clean compile
```

## Migration de base de données

```bash
psql -U postgres -d <database_name> -f sql/restaurant_table_schema.sql
psql -U postgres -d <database_name> -f sql/alter_order_schema.sql
psql -U postgres -d <database_name> -f sql/restaurant_table_data.sql
```
