import { apiRequest } from "./client";

export const dashboardReportPath = (id, month) => `/dashboard/msmes/${id}?as_of=${encodeURIComponent(month)}`;
export const requestRecommendation = (token, body, signal) => apiRequest("/recommendations", { token, method: "POST", body, signal });

export function insightsError(error, service) {
  const message = error.message || "The request could not be completed.";
  if (error.status === 502 && service === "recommendation") return message;
  if (error.status === 503 || error.status === 502) {
    return `${message} Check that the ${service} service and its dependencies are running and registered in Eureka.${service === "recommendation" ? " Evidence-only mode does not require Ollama." : ""}`;
  }
  if (error.status === 429) return `${message} Wait before submitting another request.`;
  return message;
}
