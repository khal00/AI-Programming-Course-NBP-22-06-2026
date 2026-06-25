#!/usr/bin/env node
/**
 * Generates minimal valid 1x1 JPEG fixture files for Playwright E2E tests.
 * Uses the smallest valid JPEG encoding of a 1x1 white pixel.
 *
 * Run: node scripts/generate-fixtures.js
 */

const fs = require('fs');
const path = require('path');

// Minimal valid 1x1 white JPEG (base64-encoded)
// This is a genuine JPEG that passes ImageIO.read() in the backend.
const MINIMAL_JPEG_BASE64 =
  '/9j/4AAQSkZJRgABAQEASABIAAD/2wBDAAgGBgcGBQgHBwcJCQgKDBQNDAsLDBkSEw8UHRofHh0aHBwgJC4nICIsIxwcKDcpLDAxNDQ0Hyc5PTgyPC4zNDL/2wBDAQkJCQwLDBgNDRgyIRwhMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjL/wAARCAABAAEDASIAAhEBAxEB/8QAFAABAAAAAAAAAAAAAAAAAAAACf/EABQQAQAAAAAAAAAAAAAAAAAAAAD/xAAUAQEAAAAAAAAAAAAAAAAAAAAA/8QAFBEBAAAAAAAAAAAAAAAAAAAAAP/aAAwDAQACEQMRAD8AJQAB/9k=';

const fixturesDir = path.join(__dirname, '..', 'fixtures');

if (!fs.existsSync(fixturesDir)) {
  fs.mkdirSync(fixturesDir, { recursive: true });
}

const jpegFiles = [
  'clean-return.jpg',
  'damaged-complaint.jpg',
  'contradiction.jpg',
  'unreadable.jpg',
  'llm-fail.jpg',
];

const jpegBuffer = Buffer.from(MINIMAL_JPEG_BASE64, 'base64');

for (const filename of jpegFiles) {
  const filePath = path.join(fixturesDir, filename);
  fs.writeFileSync(filePath, jpegBuffer);
  console.log(`Created: ${filePath} (${jpegBuffer.length} bytes)`);
}

// Create invalid format file
const txtPath = path.join(fixturesDir, 'notes.txt');
fs.writeFileSync(txtPath, 'This is a plain text file, not an image.\n');
console.log(`Created: ${txtPath}`);

console.log('\nAll fixtures generated successfully.');
