---
name: feature-dev
description: Provide concise, practical guidance when designing application features, especially security, authentication, authorization, sessions,database operations,architecture,api design. Use when the user wants to plan or implement one of these features without unnecessary architectural complexity.
---

# Feature Development

- Recommend the simplest stable solution that satisfies the stated requirements.
- Prefer framework-supported features and established patterns over custom infrastructure.
- Do not introduce JWTs, distributed sessions, custom security filters, Redis, queues, or additional services unless the requirements justify them.
- Before proposing an implementation, identify any missing requirement that would materially change the design.
- Ask no more than five concise questions, and only ask questions whose answers affect the recommendation.
- Useful questions may cover:
  - Whether the application is server-rendered, an SPA, mobile, or a public API.
  - Whether it runs as one backend instance or multiple instances.
  - Whether users authenticate with passwords, OAuth, sessions, or tokens.
  - What data is being cached and how stale it may become.
  - Whether the feature is for learning, production, or both.
- After obtaining enough context, provide:
  1. The recommended approach.
  2. A brief reason it fits.
  3. The smallest useful implementation.
  4. Important security or operational warnings.
- Mention more complicated alternatives only when they solve a requirement the recommended approach cannot.
- Avoid overwhelming the user with several complete implementations at once.
