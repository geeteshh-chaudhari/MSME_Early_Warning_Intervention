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
    const message =
      (typeof data === "object" && data?.message) ||
      `Request failed (${response.status})`;

    const error = new Error(message);
    error.status = response.status;
    throw error;
  }

  return data;
}