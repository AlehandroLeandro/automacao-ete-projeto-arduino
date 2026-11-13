export const STOPPAGE_OVERLAP_MESSAGE = "Já existe uma parada registrada para esta ETE nesse intervalo";

export interface StoppageInterval {
  id: string;
  startedAt: string;
  endedAt?: string | null;
}

interface OverlapCandidate {
  startAt: string;
  endAt?: string | null;
  ignoreId?: string;
}

const OPEN_ENDED = Number.POSITIVE_INFINITY;

/**
 * Pure overlap check mirroring the backend rule. Touching intervals (one ends
 * exactly when the next begins) are not considered an overlap. Open-ended
 * stoppages extend indefinitely into the future.
 */
export function hasStoppageOverlap(candidate: OverlapCandidate, existing: StoppageInterval[]): boolean {
  const candidateStart = new Date(candidate.startAt).getTime();
  const candidateEnd = candidate.endAt ? new Date(candidate.endAt).getTime() : OPEN_ENDED;

  return existing.some((item) => {
    if (candidate.ignoreId && item.id === candidate.ignoreId) {
      return false;
    }

    const existingStart = new Date(item.startedAt).getTime();
    const existingEnd = item.endedAt ? new Date(item.endedAt).getTime() : OPEN_ENDED;

    return existingStart < candidateEnd && existingEnd > candidateStart;
  });
}
