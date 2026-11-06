import {
  ReadingSpeedCalculationRequest,
  ReadingSpeedCalculationResponse,
} from '../types';

export function calculateReadingSpeedAndEta(
  request: ReadingSpeedCalculationRequest
): ReadingSpeedCalculationResponse {
  const { totalPages, currentPage, sessions } = request;
  const remainingPages = Math.max(0, totalPages - currentPage);

  // Filter valid reading sessions (duration >= 1 minute, pagesRead > 0)
  const validSessions = sessions
    .filter((s) => s.durationMinutes >= 1 && s.pagesRead > 0)
    .map((s) => {
      const pagesPerHour = (s.pagesRead / s.durationMinutes) * 60;
      return { ...s, pagesPerHour };
    })
    .filter((s) => s.pagesPerHour <= 180 && s.pagesPerHour >= 5); // discard reading anomalies

  let averagePagesPerHour: number;
  let confidence: 'LOW' | 'MEDIUM' | 'HIGH';

  if (validSessions.length === 0) {
    // Default baseline: 40 pages per hour (standard adult reading speed)
    averagePagesPerHour = 40.0;
    confidence = 'LOW';
  } else if (validSessions.length < 3) {
    // Simple average for small sample
    const sumPph = validSessions.reduce((acc, s) => acc + s.pagesPerHour, 0);
    averagePagesPerHour = Math.round((sumPph / validSessions.length) * 10) / 10;
    confidence = 'MEDIUM';
  } else {
    // Exponentially Weighted Moving Average (alpha = 0.35, favors recent reading sessions)
    const alpha = 0.35;
    let ewma = validSessions[0].pagesPerHour;
    for (let i = 1; i < validSessions.length; i++) {
      ewma = alpha * validSessions[i].pagesPerHour + (1 - alpha) * ewma;
    }
    averagePagesPerHour = Math.round(ewma * 10) / 10;
    confidence = 'HIGH';
  }

  const averageMinutesPerPage = Math.round((60 / averagePagesPerHour) * 10) / 10;
  const estimatedMinutesToFinish = Math.round((remainingPages / averagePagesPerHour) * 60);

  const hours = Math.floor(estimatedMinutesToFinish / 60);
  const mins = estimatedMinutesToFinish % 60;
  const estimatedFinishFormatted = hours > 0 ? `${hours}h ${mins}m` : `${mins}m`;

  return {
    totalPages,
    currentPage,
    remainingPages,
    averagePagesPerHour,
    averageMinutesPerPage,
    estimatedMinutesToFinish,
    estimatedFinishFormatted,
    confidenceLevel: confidence,
  };
}

