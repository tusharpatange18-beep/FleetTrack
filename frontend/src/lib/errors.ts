/**
 * Turns anything that was thrown into text we can show on screen.
 * Usually it is an Error object (use its message); if not, convert it to a string.
 */
export function errorMessage(e: unknown): string {
  if (e instanceof Error) {
    return e.message;
  }
  return String(e);
}
