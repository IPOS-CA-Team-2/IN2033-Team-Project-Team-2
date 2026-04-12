# IPOS-CA — Internal Pharmacy Operations System
### IN2033 Team Project · Team 2

IPOS-CA is the pharmacy's internal point-of-sale and management desktop application. It handles day-to-day pharmacy operations — processing sales, managing stock, tracking account holders, and placing wholesale orders with the supplier system (IPOS-SA). It is built in Java with a Swing UI and stores all data locally in SQLite.

---

## Features

| Area | What it does |
|------|-------------|
| **Login & Access Control** | Role-based login — Admin, Pharmacist, Manager each see different screens |
| **Stock Management** | View, add, edit, and delete stock items; low-stock threshold alerts |
| **Point of Sale** | Process walk-in and account holder sales; cash, credit card, and debit card payments; automatic receipt and invoice generation |
| **Account Holders** | Create accounts with credit limits and discount tiers; suspend/restore accounts; record payments; generate 1st and 2nd reminder letters |
| **Wholesale Orders** | Place orders with IPOS-SA; track order status (Pending → Accepted → Dispatched → Delivered); stock automatically increases on delivery |
| **Online Sales Sync** | CA stock decreases automatically when a customer buys via IPOS-PU's online shop |
| **Management Reports** | Turnover report (date range), stock availability with low-stock flags, aggregated debt report |

---

## Architecture

```
┌──────────────────────────────────────────────────────┐
│                      IPOS-CA                         │
│                                                      │
│  app/          Entry point, AppContext service bus   │
│  ui/           Swing screens (one per workflow)      │
│  service/      Business logic — no UI references     │
│  repository/   Interfaces + SQLite implementations   │
│  model/        Plain Java domain objects             │
│  integration/  Gateway interfaces + implementations  │
│  db/           SQLite connection + schema bootstrap  │
│  exception/    Typed exceptions per domain           │
└──────────────────────────────────────────────────────┘
         │  outbound HTTP  →  IPOS-SA  (port 8080)
         │  inbound  HTTP  ←  IPOS-SA  (port 8081)
         │  inbound  HTTP  ←  IPOS-PU  (port 8081)
```

Every layer is interface-backed. Repositories and integration gateways all have mock implementations so the entire app runs and demos without SA or PU being online.

---

## Tech Stack

| Component | Technology |
|-----------|-----------|
| Language | Java 17+ |
| UI | Java Swing |
| Database | SQLite (`sqlite-jdbc-3.36.0.3.jar`) |
| HTTP (outbound) | `java.net.http.HttpClient` (JDK built-in) |
| HTTP (inbound) | `com.sun.net.httpserver.HttpServer` (JDK built-in) |
| JSON | Gson 2.10.1 |
| Build | IntelliJ IDEA (no Maven/Gradle) |

---

## Project Structure

```
src/
├── app/
│   ├── Main.java                  ← Entry point, login screen
│   └── AppContext.java            ← Static service locator + UI refresh event bus
├── db/
│   └── DatabaseManager.java       ← SQLite connection, schema bootstrap, seeding
├── exception/
│   ├── AuthException.java
│   ├── SaleException.java
│   └── StockException.java
├── model/                         ← Domain objects (User, StockItem, Sale, etc.)
├── repository/                    ← Interfaces + SQLite implementations
├── service/                       ← All business logic
│   ├── LoginService.java
│   ├── StockService.java
│   ├── SaleService.java
│   ├── AccountService.java
│   ├── ReminderService.java
│   ├── WholesaleOrderService.java
│   ├── OnlineSaleService.java
│   └── ReportService.java
├── integration/
│   ├── ISaGateway.java            ← Interface for SA communication
│   ├── MockSaGateway.java         ← Offline mock (default)
│   ├── HttpSaGateway.java         ← Live HTTP implementation
│   ├── IPuStockUpdater.java       ← Interface for PU stock push
│   ├── MockPuAdapter.java         ← Offline mock (default)
│   ├── CaApiServer.java           ← Inbound HTTP server on port 8081
│   └── LocalDateAdapter.java      ← Gson adapter for java.time.LocalDate
└── ui/
    ├── UITheme.java               ← Shared colours, button factories, table styles
    ├── Dashboard.java             ← Role-based navigation hub
    ├── StaffManagementUI.java     ← Admin: manage staff accounts
    ├── StockManagementUI.java     ← View and manage stock
    ├── ProcessSaleUI.java         ← Full point-of-sale screen
    ├── CustomerAccountUI.java     ← Account holder management
    ├── WholesaleOrderUI.java      ← Place and track wholesale orders
    └── ReportsUI.java             ← Three-tab management reports

lib/
├── sqlite-jdbc-3.36.0.3.jar
└── gson-2.10.1.jar
```

---

## Running the Application

### Prerequisites
- Java 17 or later
- Both JARs in `lib/` on the classpath (`sqlite-jdbc-3.36.0.3.jar` and `gson-2.10.1.jar`)

### In IntelliJ IDEA
1. Open the `Team 2 - CA` folder as a project
2. Go to **File → Project Structure → Modules → Dependencies**
3. Add both JARs in `lib/` if not already present
4. Run `app.Main`

### Mock mode (no SA or PU needed)
```
java -cp "out:lib/*" app.Main
```
All SA and PU behaviour is simulated locally. The full demo works completely offline.

### Live integration mode (SA + PU must be running)
```
java -Dipos.http=true -cp "out:lib/*" app.Main
```
CA will log in to SA on startup and start the inbound HTTP server on port 8081.

---

## Integration with IPOS-SA and IPOS-PU

CA sits at the centre of the three-subsystem architecture:

```
IPOS-PU (port 8082)  ──POST /online-sale──►  IPOS-CA (port 8081)
IPOS-SA (port 8080)  ──POST /order-update──►  IPOS-CA (port 8081)
IPOS-CA              ──POST /api/orders────►  IPOS-SA (port 8080)
```

### Startup order (when running all three systems)
1. Start MySQL, then start **IPOS-SA**: `mvn spring-boot:run` (port 8080)
2. Start **IPOS-PU**: `mvn spring-boot:run` (port 8082)
3. Start **IPOS-CA** last: `java -Dipos.http=true -cp "out:lib/*" app.Main`

### Verify integration with curl
```bash
# Simulate PU pushing an online sale to CA (deducts 5 units of item 1 from stock)
curl -X POST http://localhost:8081/online-sale \
  -H "Content-Type: application/json" \
  -d '{"puOrderId":"PU-TEST","receivedDate":"2026-04-11","customerEmail":"test@test.com","items":[{"itemId":1,"quantity":5}]}'

# Expected: {"accepted":true,"fullyApplied":true}

# Simulate SA pushing an order status update to CA
curl -X POST http://localhost:8081/order-update \
  -H "Content-Type: application/json" \
  -d '{"orderId":1,"status":"CONFIRMED"}'

# Expected: {"ok":true}
```

---

## Default Credentials

| Username | Password | Role |
|----------|----------|------|
| `admin` | `admin123` | Admin |
| `pharmacist` | `pharm123` | Pharmacist |
| `manager` | `manager123` | Manager |

> These are seeded automatically on first run. Delete `ipos_ca.db` to reset the database.

---

## Database

The SQLite database file (`ipos_ca.db`) is created automatically in the working directory on first run. Schema includes:

`users` · `stock` · `customers` · `sales` · `sale_lines` · `wholesale_orders` · `wholesale_order_lines` · `online_sales` · `online_sale_items`

No setup required — delete the `.db` file at any time to start fresh.

---

## Team 2

| Name | Role |
|------|------|
| Eshan | Project Manager & Lead Developer |
