export async function apiRequest(
  path,
  { token, body, headers, ...options } = {}
) {
  const response = await fetch(`/api${path}`, {
    ...options,
    headers: {
      ...(body !== undefined
        ? { "Content-Type": "application/json" }
        : {}),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...headers,
    },
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });

  const text = await response.text();

  let data = text;
  try {
    data = text ? JSON.parse(text) : null;
  } catch {
    // Some server errors return plain text.
  }

  if (!response.ok) {
    const baseMessage =
      (typeof data === "object" && (data?.message || (typeof data?.detail === "string" && data.detail))) ||
      `Request failed (${response.status})`;
    const details = Array.isArray(data?.details)
      ? data.details.filter(item => typeof item === "string").join("; ")
      : Array.isArray(data?.detail)
        ? data.detail.map(item => item.msg).filter(Boolean).join("; ")
      : data?.validationErrors && typeof data.validationErrors === "object"
        ? Object.values(data.validationErrors).join("; ")
        : "";
    const message = response.status === 401
      ? "Authentication was rejected. Sign out and log in again; if it continues, check this service's authentication configuration."
      : details ? `${baseMessage}: ${details}` : baseMessage;

    const error = new Error(message);
    error.status = response.status;
    throw error;
  }

  return data;
}
