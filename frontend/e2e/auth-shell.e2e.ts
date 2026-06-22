import { expect, test, type Page } from '@playwright/test';

const user = {
  id: 'user-1',
  username: 'alice',
  email: 'alice@example.com',
  accountStatus: 'ACTIVE'
};

test('registers a user and opens the authenticated shell', async ({ page }) => {
  await mockApi(page);

  await page.goto('/');

  await expect(page.getByText('Mini Discord')).toBeVisible();
  await page.getByRole('button', { name: 'Register' }).first().click();
  await page.getByLabel('Username').fill('alice');
  await page.getByLabel('Email').fill('alice@example.com');
  await page.getByLabel('Password').fill('Password123!');
  await page.getByRole('button', { name: 'Register' }).last().click();

  await expect(page.getByText('Friends').first()).toBeVisible();
  await expect(page.getByTitle('Create server')).toBeVisible();
  await expect(page.getByTitle('Join server')).toBeVisible();
});

async function mockApi(page: Page) {
  await page.route('**/api/v1/**', async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname.replace('/api/v1', '');

    if (path === '/auth/refresh') {
      await route.fulfill({
        status: 401,
        contentType: 'application/json',
        body: JSON.stringify(apiError('UNAUTHENTICATED', 'Missing refresh token'))
      });
      return;
    }

    if (path === '/auth/register') {
      await route.fulfill({ contentType: 'application/json', body: JSON.stringify(apiOk({ message: 'ok' })) });
      return;
    }

    if (path === '/auth/login') {
      await route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify(apiOk({ accessToken: 'access-token', user }))
      });
      return;
    }

    if (path === '/users/me') {
      await route.fulfill({ contentType: 'application/json', body: JSON.stringify(apiOk(user)) });
      return;
    }

    if (
      path === '/servers' ||
      path === '/friends' ||
      path === '/friends/requests' ||
      path === '/direct-conversations' ||
      path === '/notifications' ||
      path === '/server-invites/received'
    ) {
      await route.fulfill({ contentType: 'application/json', body: JSON.stringify(apiOk([])) });
      return;
    }

    await route.fulfill({ contentType: 'application/json', body: JSON.stringify(apiOk({})) });
  });
}

function apiOk<T>(data: T) {
  return { success: true, data, meta: {} };
}

function apiError(code: string, message: string) {
  return {
    success: false,
    data: null,
    meta: {},
    error: { code, message, details: [], traceId: 'e2e-trace' }
  };
}
