/**
 * Call and await this at the top of any page that requires a signed-in
 * user. Two tiers on purpose: a fast local check to avoid a flash of
 * protected content while we wait on the network, then a real server-side
 * verification (the token might be expired, or revoked-by-restart if
 * you've cleared the H2 data) that's authoritative.
 */
async function requireAuth() {
  const cached = JRL.cachedUser();
  if (!cached) {
    window.location.href = 'signin.html';
    return null;
  }

  try {
    return await JRL.me();
  } catch (e) {
    JRL.signOut();
    window.location.href = 'signin.html';
    return null;
  }
}
