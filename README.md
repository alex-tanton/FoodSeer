# 🍽️ Food Seer - Smart Meal Recommendation System 🍽️

[![Build & Test](https://github.com/DemarisKeleta/CSC510-Section2-Group7-Proj3/actions/workflows/run-tests.yml/badge.svg?branch=main)](https://github.com/DemarisKeleta/CSC510-Section2-Group7-Proj3/actions/workflows/run-tests.yml)
[![M0 Eval](https://github.com/DemarisKeleta/CSC510-Section2-Group7-Proj3/actions/workflows/m0-eval.yml/badge.svg?branch=main)](https://github.com/DemarisKeleta/CSC510-Section2-Group7-Proj3/actions/workflows/m0-eval.yml)
[![License: MIT](https://img.shields.io/badge/license-MIT-yellow.svg)](LICENSE.md)

🎬 **[Watch the Teaser Video](https://vimeo.com/1237864579?share=copy&fl=sv&fe=ci)** | 🎥 **[Watch the Feature Walk-through Demo](https://vimeo.com/1237853833?share=copy&fl=sv&fe=ci)**

---

## 📌 Inherited Baseline: Project 2 Features

Food Seer is an AI-assisted smart meal recommendation and restaurant ordering system. In Project 2, the system was extended with the following milestone features and verification contracts:

| Milestone | Capability | Documentation |
| :--- | :--- | :--- |
| **M0: Risk-Killer Eval** | Measures whether allergy incident trace-back alerts matching customers (recall) without spamming others (precision) on 500 simulated incident runs. Reproducible via `mvn -Dtest=M0TraceEval test`. | [docs/allergy-trace-back.md](docs/allergy-trace-back.md#m0-the-risk-killer-eval) |
| **Allergy Trace-Back** | When an allergic reaction is reported, Food Seer traces all customers who ordered the dish in the affected window with matching allergies and delivers targeted safety notices. | [docs/allergy-trace-back.md](docs/allergy-trace-back.md) |
| **Fitness Calorie Tracking** | Integrates customer fitness activity data into daily calorie budgets, tracks meals consumed, and recommends entrees that fit remaining daily targets. | [docs/personal-diet.md](docs/personal-diet.md) |
| **Pantry-Paired Ordering** | Matches menu items against ingredients currently available in a customer's pantry, calculating match percentage, missing items, and prep steps. | [docs/pantry-pairing.md](docs/pantry-pairing.md) |
| **AI Assistant Chatbot** | Conversational recommendation assistant powered by local Ollama LLMs (`gemma3:1b` / `qwen2.5:1.5b`) that filters meals based on budget, mood, and dietary restrictions. | In-app (`/chatbot`) |

> **Note on M0**: The M0 badge reports the acceptance claim from Project 2. The pipeline runs automatically on pull requests and pushes to `main`.

---

## 💡 About Food Seer

Food Seer simplifies food decisions through guided personalization, eliminating menu decision fatigue:

* **Customers**: Receive conversational recommendations via the AI Chatbot, take mood-based quizzes, pair meals with pantry inventory, track calories against fitness targets, and filter by allergies.
* **Restaurant / Staff**: Monitor incoming orders, update live food inventory, manage roles, and review/dispatch allergy incident alerts.
* **Drivers**: View open orders with transparent compensation and distance details, claim orders, and track delivered order history.

---

## 🧱 Repository Structure

```text
FoodSeer/
├── docs/                      # Architectural & feature design contracts
│   ├── allergy-trace-back.md  # Allergy trace-back and M0 eval documentation
│   ├── pantry-pairing.md      # Pantry-to-recipe matching design
│   └── personal-diet.md       # Fitness-based calorie budgeting
├── food-seer-backend/         # Spring Boot 3 / Java 21 REST API
│   ├── pom.xml                # Maven build with JaCoCo, SpotBugs, PMD
│   ├── src/main/java/         # Application controllers, services, repositories
│   └── src/test/java/         # JUnit 5 & Mockito test suites
├── food-seer-frontend/        # React 18 single-page application
│   ├── package.json           # Frontend dependencies, ESLint, Prettier, Jest
│   └── src/                   # Components, pages, and UI services
└── .github/workflows/         # GitHub Actions CI (run-tests.yml, m0-eval.yml)
```

---

## 🛠️ Technology Stack

* **Backend**: Java 21, Spring Boot 3.1.4, Spring Security, Spring Data JPA, Hibernate, MySQL, JJWT
* **Frontend**: React 18, React Router v6, Chart.js, React-Chartjs-2
* **AI & Intelligence**: Ollama local LLM runtime (`qwen2.5:1.5b` / `gemma3:1b`)
* **Testing & Quality Assurance**:
  * **Backend**: JUnit 5, Mockito, JaCoCo (Coverage), Pitest (Mutation), SpotBugs (Dead Code / Bug Patterns), PMD & CPD (Code Quality & Duplicate Detection)
  * **Frontend**: Jest, React Testing Library, ESLint, Prettier, Depcheck

---

## 🚀 Setup & Local Execution

### 1. Prerequisites
* **Java JDK 21+** & **Maven 3.8+**
* **Node.js 18+** & **npm**
* **MySQL 8.0+**
* **Ollama** (optional, required only for local chatbot features)

### 2. Database Setup
Ensure MySQL is running on `localhost:3306`. The application expects a database named `users`:
```sql
CREATE DATABASE IF NOT EXISTS users;
```

### 3. Backend Setup
1. **Configure Application Properties**:
   Copy the template and configure your local credentials:
   ```bash
   cd food-seer-backend
   cp src/main/resources/application.properties.template src/main/resources/application.properties
   ```
   In `src/main/resources/application.properties`, update:
   * `spring.datasource.password`: Your local MySQL root password.
   * `app.jwt-secret`: A 256-bit secret key (or generate with SHA-256).
   * `app.admin-user-password`: The default admin user password.

2. **Run Backend Application**:
   ```bash
   mvn spring-boot:run
   ```
   The backend starts at `http://localhost:8080`.

### 4. Frontend Setup
```bash
cd food-seer-frontend
npm install
npm start
```
The frontend starts at `http://localhost:3000`. Default admin login: `admin` / (your configured password).

### 5. Ollama Chatbot Setup (Optional)
If running the conversational AI assistant locally:
1. Install [Ollama](https://ollama.com).
2. Pull the model:
   ```bash
   ollama pull gemma3:1b
   ```
3. Start the Ollama server:
   ```bash
   ollama serve
   ```
   The service listens on `http://localhost:11434`.

---

## 🧪 Consistency & Code Quality

Both backend and frontend are configured with consistency, dead code detection, and linting tools:

### Backend Quality Commands
```bash
cd food-seer-backend

# Run unit tests and generate JaCoCo coverage report
mvn clean test

# Run the 14 focused driver-statistics security authorization tests
mvn -Dtest=DriverStatsSecurityTest,DriverStatsControllerTest,DriverStatsImplTest test

# Run SpotBugs static analysis (detects bug patterns and dead code)
mvn spotbugs:check

# Run PMD / CPD (detects unused variables/imports, design flaws, and duplicates)
mvn pmd:check pmd:cpd-check
```

### Frontend Quality Commands
```bash
cd food-seer-frontend

# Run unit tests with coverage
npm run test:coverage

# Lint source files with ESLint
npm run lint

# Auto-format files with Prettier
npm run format

# Verify formatting without modifying files
npm run format:check

# Check for unused dependencies and dead packages
npm run dead-code
```

---

## 🎯 Project 3 Scope & Extensions

This repository serves as the baseline for **Project 3: Maintain & Extend**. The upcoming work will build directly upon this foundation:

1. **Four Major Milestones**: High-impact feature expansions to be planned and delivered.
2. **Regression Guarantees**: All inherited Project 2 unit tests and M0 trace-back evals continue passing.
3. **Delta Clarity**: Distinct, clean architecture isolating new Project 3 additions while respecting the inherited foundation.

---

## 📄 License & Contributing

* **License**: Distributed under the [MIT License](LICENSE.md).
* **Contributing**: Guidelines and branch workflow can be found in [CONTRIBUTING.md](CONTRIBUTING.md).
* **Code of Conduct**: Community guidelines can be found in [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md).
