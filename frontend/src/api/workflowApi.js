import { apiRequest } from "./client";

export const assessmentHistoryPath = (id, page) => `/assessment/msmes/${id}/assessments?page=${page}&size=10`;
export const thresholdHistoryPath = (id, page) => `/assessment/msmes/${id}/thresholds?page=${page}&size=10`;
export const warningHistoryPath = (id, page) => `/warnings/${id}/history?page=${page}&size=10`;
export const warningsPath = (id, page, status = "") => `/warnings?msmeId=${id}&page=${page}&size=10${status ? `&status=${encodeURIComponent(status)}` : ""}`;

export const runAssessment = (token, id, month) => apiRequest("/assessment/assessments/from-finance", {
  token, method: "POST", body: { msmeId: Number(id), reportingPeriod: `${month}-01` },
});
export const createThreshold = (token, body) => apiRequest("/assessment/thresholds", { token, method: "POST", body });
export const reviewThreshold = (token, id, body) => apiRequest(`/assessment/thresholds/${id}/review`, { token, method: "POST", body });
export const createWarning = (token, assessmentId) => apiRequest("/warnings", { token, method: "POST", body: { assessmentId } });
export const decideWarning = (token, id, body) => apiRequest(`/warnings/${id}/decisions`, { token, method: "POST", body });
