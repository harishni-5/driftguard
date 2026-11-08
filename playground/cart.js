// Demo only: planted drift so the DriftGuard PR comment has something to report.
export async function cartTotal(account) {
  const items = await fetch('/items/' + account).then(r => r.json());
  const active = items.filter(i => i.active);
  const total = active.reduce((sum, i) => sum + i.price, 0);
  const label = '$' + total.toFixed(2);
  return { label, total };
}
