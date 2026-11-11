# SOLID Principles of Programming Policy

This policy mandates strict adherence to the **SOLID principles of object-oriented and component-driven software design** for any future enhancements, bug fixes, refactors, or new features added to the AuditAgent codebase across both Frontend and Backend.

---

## 1. Single Responsibility Principle (SRP)

> *A module, class, hook, or component should have one, and only one, reason to change.*

- **Component Decomposition**: Never build monolithic "god components". Components must not combine state management, API calls, streaming protocol handling, layout, and complex DOM rendering in a single file.
- **Hook Extraction**: Extract business logic, complex filtering/sorting, form states, and timers into dedicated custom hooks (e.g., `useFindingsFilter`, `useLlmConfigForm`, `useScanElapsedSeconds`).
- **File Length Guideline**: Strive to keep presentation components under 200 lines of code. If a component exceeds this, actively decompose it into focused sub-components co-located in a `components/` sub-directory.
- **Backend Services**: Separate persistence, orchestration, AI prompt building, and external CLI/HTTP client execution into distinct domain services.

---

## 2. Open / Closed Principle (OCP)

> *Software entities should be open for extension, but closed for modification.*

- **Declarative Registries**: Extend UI capabilities via declarative registries rather than hardcoding conditional branches in core layout shells.
  - **Tabs & Views**: Register tabs in `workspaceTabs.jsx` (`WORKSPACE_TABS` array with id, label, icon, badge calculator, and view renderer). Do not add `activeTab === 'my-new-tab'` switch statements to layout containers.
  - **Modals & Overlays**: Manage modals through `ModalContext` (`useModals()`) and `ModalManager.jsx` rather than creating separate boolean states and inline JSX blocks in top-level shells.
- **Extensible Adapters**: For security scanners, LLM providers, and report formats, use polymorphic interfaces or strategy registries so new engines can be added without modifying existing workflow controllers.

---

## 3. Liskov Substitution Principle (LSP)

> *Subtypes or interface implementations must be substitutable for their base types without altering program correctness.*

- **Consistent Contracts**: Any implementation of an API client or service interface must fully satisfy the expected contract (error shapes, returned data structures, cancellation support).
- **Test Harness Compatibility**: When authoring or testing components, use the standardized `renderWithProviders()` test utility from `src/test/test-utils.jsx`. Mock API clients passed to `<ApiProvider api={mockApi}>` must behave predictably without side effects.
- **Normalization Invariants**: Data returned by scans (fresh, cached, upload, or restored) must pass through `normalizeScanResult()` to ensure invariant object shapes across the application.

---

## 4. Interface Segregation Principle (ISP)

> *Clients should not be forced to depend upon interfaces, props, or methods they do not use.*

- **Eliminate Prop Drilling**: Never pass monolithic objects or 10+ callback props through intermediate components.
- **Focused Domain Hooks**: Subscribe only to the specific slice of domain context needed:
  - `useAuth()` for user session, authentication status, and login/logout.
  - `useWorkspace()` for repository selection, branches, and scan target configuration.
  - `useAudit()` for findings, scan execution, and remediation state.
  - `useModals()` for opening/closing dialogs, confirmation dialogs, and drawer visibility.
  - `useApi()` for interacting with backend REST/SSE services.
- **Granular Component Props**: Presentational sub-components must declare minimal, explicit prop interfaces rather than receiving entire parent states.

---

## 5. Dependency Inversion Principle (DIP)

> *High-level modules should not depend on low-level modules; both should depend on abstractions.*

- **Context-Driven Injection**: Presentation components must consume dependencies via React Context abstractions (`useApi()`, `useAuth()`, etc.) rather than importing concrete singleton instances directly.
- **Inversion of Control**: Root containers (`AppProviders.jsx`) assemble and inject concrete implementations, allowing unit tests, dev harnesses, and production builds to substitute dependencies effortlessly.
- **Backend Inversion**: Spring services must depend on repository interfaces and abstract client interfaces rather than concrete process executors or direct storage implementations.

---

## 6. Pre-Commit Enforcement Checklist

Before submitting changes for user review, verify:
1. **SRP**: Are large components decomposed into focused sub-components and custom hooks?
2. **OCP**: Are new views or dialogs integrated via declarative registries (`workspaceTabs`, `ModalManager`)?
3. **LSP**: Do test suites pass 100% green using `renderWithProviders()`?
4. **ISP**: Is prop-drilling eliminated in favor of focused domain context hooks?
5. **DIP**: Are API calls routed through `useApi()` or domain providers rather than static imports?
6. **Quality**: Does `npm run lint` report 0 errors and `npm run test:run` pass 100%?
