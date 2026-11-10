# Welcome to *Food Seer* Contributing Guide

We're thrilled you're interested in contributing! This guide outlines the steps for getting involved, from submitting issues to crafting and merging pull requests.

Before you begin, please take a moment to read our [Code of Conduct](CODE_OF_CONDUCT.md). It’s essential for fostering a respectful, inclusive, and collaborative community.

---

## Table of Contents
1. [How Can I Contribute?](#how-can-i-contribute)
2. [Submitting a Pull Request](#submitting-a-pull-request)
3. [Coding Standards](#coding-standards)
4. [Contributor IP & Copyright](#contributor-ip--copyright)

---

## How Can I Contribute?

#### 1. Discuss Changes
- Before making significant changes, please discuss ideas with the project maintainers through GitHub Issues or pull request drafts.

#### 2. Creating Issues
- Review existing issues before opening a new issue to avoid duplicates.
- When reporting a bug, include reproduction steps, expected behavior, and environment details.

#### 3. Addressing Code of Conduct Violations
- If you witness or experience behavior that breaches our Code of Conduct, notify the project maintainers according to [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md).

---

## Submitting a Pull Request

1. **Create a Feature Branch**: Branch off `main` with a descriptive name, e.g., `feature/pantry-upgrade` or `bugfix/issue-12`.
2. **Implement Your Changes**: Adhere to the established coding standards and architecture.
3. **Write and Run Tests**: Include tests for all new capabilities and verify that all existing tests pass (`mvn test`, `npm test`).
4. **Run Consistency & Quality Checks**:
   - Backend: `mvn spotbugs:check pmd:check`
   - Frontend: `npm run lint` and `npm run format:check`
5. **Open a Pull Request**: Submit your pull request against `main` with a clear description of the delta.

---

## Coding Standards

To maintain consistency across Food Seer, follow these standards:

### Backend (Java) Standards
- **Naming Conventions**: camelCase for methods and variables, PascalCase for classes and interfaces.
- **Static Analysis**: We use **SpotBugs** for bug pattern and dead code detection:
  ```bash
  mvn spotbugs:check
  ```
- **Code Quality & Duplicate Detection**: We use **PMD & CPD**:
  ```bash
  mvn pmd:check pmd:cpd-check
  ```

### Frontend (JavaScript/React) Standards
- **Formatting**: We use **Prettier** for automated formatting:
  ```bash
  npm run format
  npm run format:check
  ```
- **Linting**: We use **ESLint** to verify code quality:
  ```bash
  npm run lint
  npm run lint:fix
  ```
- **Dead Code Check**: Verify dependencies with **depcheck**:
  ```bash
  npm run dead-code
  ```

---

## Contributor IP & Copyright

Contributors retain ownership of their contributions while granting the project license under the [MIT License](LICENSE.md).
