# Entregables del Proyecto de Aula (Power BI)

| Entregable | Archivo | Estado |
|---|---|---|
| Documento con integrantes, titulo, descripcion y enlace | `Documento_Entrega_Proyecto_Aula.docx` | Completar nombres de integrantes y enlace (texto en rojo) |
| Datos del tablero (ejemplo, listos para Power BI) | `powerbi/SyncFlow_datos.xlsx` (y `powerbi/datos_csv/`) | Listo |
| Archivo Power BI del tablero final | `powerbi/SyncFlow_Tablero.pbix` | **Pendiente**: se crea en Power BI Desktop |

## Datos incluidos (ficticios, con la misma estructura que la base de SyncFlow)
15 usuarios, 2 organizaciones, 11 membresias (2 ya salieron), 90 eventos (30 + 28 organizacionales, 32 personales; 28 recurrentes),
242 asignaciones (17 con conflicto de horario), 158 tareas de checklist. Fechas: agosto a diciembre de 2026.
Los correos son @example.com y no hay contrasenas reales.

## Camino facil: Excel (no necesitas Docker ni PostgreSQL)
1. Abre **Power BI Desktop** > Inicio > **Obtener datos > Libro de Excel** y elige `powerbi/SyncFlow_datos.xlsx`.
2. Marca las 5 hojas (`eventos`, `asignaciones`, `membresias`, `checklist`, `usuarios`) y pulsa **Cargar**.
3. Vista **Modelo**: arrastra `asignaciones[event_id]` a `eventos[id]` y `checklist[event_id]` a `eventos[id]` (muchos a uno).
4. Crea las medidas (Modelado > Nueva medida):
```dax
Total Eventos = COUNTROWS(eventos)
Total Asignaciones = COUNTROWS(asignaciones)
Asignaciones con Conflicto = CALCULATE(COUNTROWS(asignaciones), asignaciones[tiene_conflicto] = TRUE())
% Conflictos = DIVIDE([Asignaciones con Conflicto], [Total Asignaciones])
Tareas Completadas = CALCULATE(COUNTROWS(checklist), checklist[completada] = TRUE())
% Checklist Completado = DIVIDE([Tareas Completadas], COUNTROWS(checklist))
Eventos Recurrentes = CALCULATE(COUNTROWS(eventos), eventos[recurrencia] <> "NONE")
Membresias Activas = CALCULATE(COUNTROWS(membresias), membresias[activa] = TRUE())
Horas Programadas = SUM(eventos[duracion_horas])
```
5. Visuales sugeridos:
   - Tarjetas: Total Eventos, % Conflictos, % Checklist Completado, Membresias Activas.
   - Columnas: eventos por mes (`eventos[inicio]`, nivel Mes).
   - Barras: eventos por `organizacion`; Horas Programadas por `organizacion`.
   - Anillo: eventos por `recurrencia` (DAILY, WEEKLY, NONE) o por `tipo_evento`.
   - Medidor: % Checklist Completado.
   - Tabla: membresias (`usuario`, `organizacion`, `rol`, `activa`).
   - Barras: asignaciones con conflicto por `empleado`.
   - Segmentadores: `organizacion` y `inicio`.
6. Guarda como `SyncFlow_Tablero.pbix`, publica (Inicio > Publicar) y pega el enlace en el documento.

## Camino alterno: PostgreSQL real (usa los datos de la app)
1. `docker compose up --build -d` en la raiz del proyecto (BD `saas_calendar`, usuario `root`, clave `rootpassword`, puerto 5432).
2. Ejecuta en orden en pgAdmin/DBeaver: `1_esquema.sql` (solo si la base esta vacia), `2_datos_ejemplo.sql` (opcional, borra los datos existentes), `3_vistas_powerbi.sql`.
3. En Power BI: Obtener datos > Base de datos PostgreSQL > `localhost:5432` / `saas_calendar`; carga las vistas `vw_*`.
   Las columnas se llaman igual que en el Excel.
