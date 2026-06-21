# PlatformCon 2026 — Call for Proposals

## Title

**Quality by Design: Embedding DevSecOps and Test Automation into CI/CD Pipeline**

## Theme

**Theme 2: Platform Engineering in Practice** — Platform as a Product

## Format

Live workshop · ~2 hours · virtual, hands-on

## Description

A hands-on workshop on embedding DevSecOps and test automation into CI/CD pipelines using reusable workflow templates, security-by-default checks, ephemeral test environments, and enforceable quality gates.

## Abstract

Teams rarely lack tests or security — they lack consistency. One service has strong gates, the next has none, and the platform team ends up maintaining one-off pipelines instead of improving the platform.

In this live workshop, participants turn DevSecOps and test automation into CI/CD defaults. Using a self-contained demo repo, they wire up a standard gate chain, adopt reusable GitHub Actions workflow templates, and spin up ephemeral test environments.

By the end of the workshop, participants will be able to:

- Design a test automation strategy as a CI/CD gate policy
- Build opinionated, reusable CI/CD pipeline templates
- Provision self-service ephemeral test environments for safer validation
- Implement quality gates that are fast, enforceable, and actionable
- Create feedback loops that improve adoption and reduce flaky gate failures

Practical guardrails include secrets and dependency checks, SBOM generation and scanning, and policy-as-code for Kubernetes manifests (OPA/Conftest) — fast feedback and safer releases without slowing delivery.

## Tools

| Tool | Role in workshop |
|------|------------------|
| GitHub Actions | Reusable workflow templates and gate orchestration |
| Docker | Local builds and ephemeral Compose environments in CI |
| Kubernetes (optional) | Namespace-per-PR scripts in `platform/scripts/` |
| OPA / Conftest | Policy-as-code for K8s manifests |
| Syft | SBOM generation |
| Trivy / npm audit | Vulnerability scanning |
| Gitleaks | Secrets scanning |
| Gradle + Vitest | Unit, contract, and integration tests |

## Gate chain implemented in this repo

```
unit → contract → integration (BAT) → ephemeral env → smoke → perf smoke → promote
              ↕
        DevSecOps defaults (parallel)
```

Orchestrator: [`.github/workflows/quality-by-design.yaml`](../.github/workflows/quality-by-design.yaml)

## Technical requirements

- GitHub account (for Actions concepts; demo runs locally too)
- Docker Desktop installed
- Optional: Kubernetes cluster (kind/minikube) for environment provisioning exercises

## Facilitator guide

See [WORKSHOP-GUIDE.md](WORKSHOP-GUIDE.md) for the hands-on script and file map.
