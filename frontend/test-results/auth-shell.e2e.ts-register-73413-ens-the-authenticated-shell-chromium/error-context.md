# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: auth-shell.e2e.ts >> registers a user and opens the authenticated shell
- Location: e2e/auth-shell.e2e.ts:10:1

# Error details

```
Test timeout of 30000ms exceeded.
```

```
Error: page.goto: Test timeout of 30000ms exceeded.
Call log:
  - navigating to "http://127.0.0.1:5173/", waiting until "load"

```

# Test source

```ts
  1  | import { expect, test, type Page } from '@playwright/test';
  2  | 
  3  | const user = {
  4  |   id: 'user-1',
  5  |   username: 'alice',
  6  |   email: 'alice@example.com',
  7  |   accountStatus: 'ACTIVE'
  8  | };
  9  | 
  10 | test('registers a user and opens the authenticated shell', async ({ page }) => {
  11 |   await mockApi(page);
  12 | 
> 13 |   await page.goto('/');
     |              ^ Error: page.goto: Test timeout of 30000ms exceeded.
  14 | 
  15 |   await expect(page.getByText('Mini Discord')).toBeVisible();
  16 |   await page.getByRole('button', { name: 'Register' }).first().click();
  17 |   await page.getByLabel('Username').fill('alice');
  18 |   await page.getByLabel('Email').fill('alice@example.com');
  19 |   await page.getByLabel('Password').fill('Password123!');
  20 |   await page.getByRole('button', { name: 'Register' }).last().click();
  21 | 
  22 |   await expect(page.getByText('Friends').first()).toBeVisible();
  23 |   await expect(page.getByTitle('Create server')).toBeVisible();
  24 |   await expect(page.getByTitle('Join server')).toBeVisible();
  25 | });
  26 | 
  27 | async function mockApi(page: Page) {
  28 |   await page.route('**/api/v1/**', async (route) => {
  29 |     const request = route.request();
  30 |     const url = new URL(request.url());
  31 |     const path = url.pathname.replace('/api/v1', '');
  32 | 
  33 |     if (path === '/auth/refresh') {
  34 |       await route.fulfill({
  35 |         status: 401,
  36 |         contentType: 'application/json',
  37 |         body: JSON.stringify(apiError('UNAUTHENTICATED', 'Missing refresh token'))
  38 |       });
  39 |       return;
  40 |     }
  41 | 
  42 |     if (path === '/auth/register') {
  43 |       await route.fulfill({ contentType: 'application/json', body: JSON.stringify(apiOk({ message: 'ok' })) });
  44 |       return;
  45 |     }
  46 | 
  47 |     if (path === '/auth/login') {
  48 |       await route.fulfill({
  49 |         contentType: 'application/json',
  50 |         body: JSON.stringify(apiOk({ accessToken: 'access-token', user }))
  51 |       });
  52 |       return;
  53 |     }
  54 | 
  55 |     if (path === '/users/me') {
  56 |       await route.fulfill({ contentType: 'application/json', body: JSON.stringify(apiOk(user)) });
  57 |       return;
  58 |     }
  59 | 
  60 |     if (
  61 |       path === '/servers' ||
  62 |       path === '/friends' ||
  63 |       path === '/friends/requests' ||
  64 |       path === '/direct-conversations' ||
  65 |       path === '/notifications' ||
  66 |       path === '/server-invites/received'
  67 |     ) {
  68 |       await route.fulfill({ contentType: 'application/json', body: JSON.stringify(apiOk([])) });
  69 |       return;
  70 |     }
  71 | 
  72 |     await route.fulfill({ contentType: 'application/json', body: JSON.stringify(apiOk({})) });
  73 |   });
  74 | }
  75 | 
  76 | function apiOk<T>(data: T) {
  77 |   return { success: true, data, meta: {} };
  78 | }
  79 | 
  80 | function apiError(code: string, message: string) {
  81 |   return {
  82 |     success: false,
  83 |     data: null,
  84 |     meta: {},
  85 |     error: { code, message, details: [], traceId: 'e2e-trace' }
  86 |   };
  87 | }
  88 | 
```