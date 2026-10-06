# Fresh start / troubleshooting

## Recommended: rebuild the database from scratch

MySQL user `root`, password `root`.

```bash
mysql -u root -proot < database/vitc_db_fresh.sql      # drops + recreates vitc_db
mysql -u root -proot vitc_db < database/seed_data.sql  # optional demo content
cd backend && mvn spring-boot:run
```

Then open <http://localhost:8080/swagger-ui.html> and log in to the admin panel
with `admin / Admin@123`.

## Errors this fixes

**`Error Code: 3780 ... Referencing column '<x>_id' and referenced column 'id'
in foreign key constraint ... are incompatible`**
The old schema used `BIGINT UNSIGNED` / `INT UNSIGNED` keys while the JPA
entities use `Long` (signed `BIGINT`). `SET FOREIGN_KEY_CHECKS = 0` does not
help — it skips row checks only, MySQL still refuses a type change while a
foreign key points at the column. The new script creates every key as signed
`BIGINT`, so there is nothing to repair.

**Hibernate `wrong column type` / `incompatible data types` warnings on startup**
Same root cause; gone with the new schema.

**`Duplicate entry 'admin@vitc.in' for key 'admins.uq_admins_email'`**
`AdminSeeder` now checks the username *and* the email before inserting and
swallows a constraint violation, so seeding can never stop startup.

## `NoClassDefFoundError` / `ClassNotFoundException` for classes that clearly
## exist in `src/` (e.g. `AssignmentMapper`, `TestimonialMapper`, `CourseMapper`,
## `ErrorResponse`, `CurrentUserContext`, `ForgotPasswordRequest`)

This is **not** a missing-source-file problem — every class in that error is
present under `src/main/java/com/vitc/...`. It means the JVM is running an
**old, out-of-date `target/classes` directory** that was built before those
files existed (or before the newest edits to `security/*` from PART 11B-1),
and it was started without a fresh compile — typically by re-running a stale
"Application" run configuration in an IDE, or `java -cp target/classes ...`
directly, instead of rebuilding first.

Fix — force a full clean rebuild:

```bash
cd backend
mvn clean compile        # or: mvn clean package -DskipTests
mvn spring-boot:run
```

If using an IDE (IntelliJ/Eclipse), also do **File → Invalidate Caches / Rebuild
Project** (IntelliJ) or **Project → Clean** (Eclipse) once, then re-run —
IDEs cache compiled `.class` files per-file and can miss newly added files in
a package until a full rebuild is forced. Delete any leftover `target/`
directory by hand first if `mvn clean` doesn't remove it for some reason.

## Notes

* `spring.jpa.hibernate.ddl-auto=update` stays on, and now agrees with the
  script — Hibernate finds the tables it expects and changes nothing.
* Nothing needs to be dropped by hand. Re-running `vitc_db_fresh.sql` always
  gives a clean database (it starts with `DROP DATABASE IF EXISTS vitc_db`).
* If MySQL rejects the login, confirm the password: `mysql -u root -proot -e "select 1"`.
  Update `backend/src/main/resources/application.properties` if yours differs.
