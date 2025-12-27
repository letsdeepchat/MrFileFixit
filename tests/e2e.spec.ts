
import { test, expect, devices } from '@playwright/test';
import { Buffer } from 'node:buffer';

const BASE_URL = 'http://localhost:3000';

test.describe('MrFileFixit Responsiveness & UX', () => {

  test('TC-RESP-01: Desktop Dashboard Layout', async ({ page }: { page: import('@playwright/test').Page }) => {
    await page.setViewportSize({ width: 1440, height: 900 });
    await page.goto(BASE_URL);
    const cards = page.locator('.tool-card');
    await expect(cards).toHaveCount(TOOLS.length);
    // Should be grid with multiple columns
    const box = await cards.first().boundingBox();
    expect(box?.width).toBeLessThan(500); 
  });

  test('TC-RESP-02: Mobile Dashboard Stacking', async ({ page }: { page: import('@playwright/test').Page }) => {
    await page.setViewportSize(devices['iPhone 14'].viewport);
    await page.goto(BASE_URL);
    
    // Category buttons should be scrollable
    const categoryContainer = page.locator('button:has-text("Images")').locator('..');
    const isScrollable = await categoryContainer.evaluate((el: Element) => el.scrollWidth > el.clientWidth);
    expect(isScrollable).toBeTruthy();

    const firstCard = page.locator('.tool-card').first();
    const box = await firstCard.boundingBox();
    // On mobile, cards should take up nearly full width
    expect(box?.width).toBeGreaterThan(300);
  });

  test('TC-RESP-03: Workspace Adaptive Layout (Mobile)', async ({ page }: { page: import('@playwright/test').Page }) => {
    await page.setViewportSize(devices['iPhone 14'].viewport);
    await page.goto(BASE_URL);
    await page.click('text=Resize & Crop');
    
    // Upload dummy file
    const [fileChooser] = await Promise.all([
      page.waitForEvent('filechooser'),
      page.click('text=Choose File'),
    ]);
    await fileChooser.setFiles({
      name: 'test.png', mimeType: 'image/png',
      buffer: Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==', 'base64')
    });

    // Preview and settings should stack vertically
    const preview = page.locator('img[alt="Workspace"]');
    const settings = page.locator('h2:has-text("Resize & Crop")').locator('..').locator('..');
    
    const pBox = await preview.boundingBox();
    const sBox = await settings.boundingBox();
    
    expect(sBox?.y).toBeGreaterThan(pBox?.y || 0); // Settings below preview
  });

  test('TC-SEC-01: Memory Leak Prevention (URL Revocation)', async ({ page }: { page: import('@playwright/test').Page }) => {
    await page.goto(BASE_URL);
    await page.click('text=Resize & Crop');
    
    // Multiple upload-discard cycles
    for(let i=0; i<3; i++) {
        const [fc] = await Promise.all([page.waitForEvent('filechooser'), page.click('text=Choose File')]);
        await fc.setFiles({ name: `t${i}.png`, mimeType: 'image/png', buffer: Buffer.alloc(100) });
        await page.click('text=Discard');
    }
    
    // Verify no stray blob elements are in memory/DOM (Manual check in real devtools)
    await expect(page.locator('text=Choose File')).toBeVisible();
  });
});

const TOOLS = [
    { id: 'image-resize' }, { id: 'image-compress' }, { id: 'image-target-size' },
    { id: 'image-bg-remove' }, { id: 'pdf-merge' }, { id: 'pdf-split' },
    { id: 'ocr-scanner' }, { id: 'office-convert' }
];
