# V38 collision diagnostic fixture

`t1/V38__durable_private_file_scans.sql` is copied unchanged (LF) from DEV ref
`origin/feature/t1-07-file-scanning`, commit
`32478e6b4a7431b33989a8ac318a5cc46fd9eb4b`.

Only `Week4MigrationCollisionIT` explicitly loads this location. It is outside
`db/migration` and is not a production migration or an integration decision.
The test expects duplicate-version / history-validation rejection. Passing the
test does **not** mean the two branches can be merged or their databases upgraded.
