// Signature hero element: a live token-bucket simulation. Dots are requests;
// the bucket has 5 tokens and refills 1/900ms. Whichever state the bucket is
// in when a dot is spawned decides its fate — same logic the real
// TokenBucketAlgorithm Lua script runs against Redis, just visualized.
(function () {
  const stage = document.getElementById('flowStage');
  if (!stage) return;

  const allowedEl = document.getElementById('flowAllowed');
  const deniedEl = document.getElementById('flowDenied');

  let tokens = 5;
  const capacity = 5;
  let allowed = 0;
  let denied = 0;

  setInterval(() => { tokens = Math.min(capacity, tokens + 1); }, 900);

  function spawnDot() {
    const isAllowed = tokens > 0;
    if (isAllowed) tokens -= 1;

    const dot = document.createElement('div');
    dot.className = 'flow-dot';
    const laneY = 32 + Math.random() * 36;
    dot.style.top = laneY + '%';
    stage.appendChild(dot);

    requestAnimationFrame(() => {
      dot.style.transition =
        'left 1.05s linear, top 0.5s ease 0.55s, opacity 0.4s ease 0.9s, background 0.2s ease 0.5s';
      dot.style.left = '60%';
    });

    setTimeout(() => {
      dot.style.background = isAllowed ? 'var(--allow)' : 'var(--deny)';
      dot.style.left = '104%';
      dot.style.top = isAllowed ? '10%' : '90%';
    }, 560);

    setTimeout(() => { dot.style.opacity = '0'; }, 900);

    setTimeout(() => {
      dot.remove();
      if (isAllowed) {
        allowed += 1;
        if (allowedEl) allowedEl.textContent = String(allowed);
      } else {
        denied += 1;
        if (deniedEl) deniedEl.textContent = String(denied);
      }
    }, 1400);
  }

  spawnDot();
  setInterval(spawnDot, 420);
})();
