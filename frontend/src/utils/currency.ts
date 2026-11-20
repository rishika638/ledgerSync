const MAX_SAFE_MINOR_UNITS = BigInt(Number.MAX_SAFE_INTEGER);

export function formatMinorUnits(minorUnits: number): string {
  if (!Number.isSafeInteger(minorUnits)) {
    throw new RangeError('Amount exceeds JavaScript safe integer precision.');
  }

  const amount = BigInt(minorUnits);
  const sign = amount < 0n ? '-' : '';
  const absolute = amount < 0n ? -amount : amount;
  const whole = (absolute / 100n).toString();
  const groupedWhole = whole.length <= 3
    ? whole
    : `${whole.slice(0, -3).replace(/\B(?=(\d{2})+(?!\d))/g, ',')},${whole.slice(-3)}`;
  const paise = (absolute % 100n).toString().padStart(2, '0');

  return `${sign}${groupedWhole}.${paise}`;
}

export function parseRupeesToMinorUnits(value: string): number {
  const match = /^(-?)(0|[1-9]\d*)(?:\.(\d{1,2}))?$/.exec(value.trim());
  if (!match) {
    throw new Error('Enter an amount with at most two decimal places.');
  }

  const rupees = BigInt(match[2]);
  const paise = BigInt((match[3] || '').padEnd(2, '0') || '0');
  const absoluteMinorUnits = rupees * 100n + paise;
  const minorUnits = match[1] === '-' ? -absoluteMinorUnits : absoluteMinorUnits;
  if (minorUnits > MAX_SAFE_MINOR_UNITS || minorUnits < -MAX_SAFE_MINOR_UNITS) {
    throw new RangeError('Amount exceeds JavaScript safe integer precision.');
  }
  return Number(minorUnits);
}
