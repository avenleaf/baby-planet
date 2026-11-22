export function hasSteps(activity) {
  return Array.isArray(activity?.steps) && activity.steps.length > 0 &&
    activity.steps.every(step => typeof step === 'string' && step.trim());
}

export function readActivities(key) {
  try {
    const rows = JSON.parse(localStorage.getItem(key) || '[]');
    return Array.isArray(rows) ? rows.filter(row =>
      typeof row?.title === 'string' && Number.isInteger(row.i) && row.i >= 0 && row.i < 4 &&
      (key !== 'planetSaved' || hasSteps(row))) : [];
  } catch {
    return [];
  }
}

export function sameActivity(a, b) {
  return a.i === b.i && a.title === b.title && JSON.stringify(a.steps) === JSON.stringify(b.steps);
}
