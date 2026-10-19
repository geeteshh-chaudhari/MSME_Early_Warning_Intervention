import { apiRequest } from "./client";

export function createBusiness(token, values) {
  return apiRequest("/msmes", {
    method: "POST",
    token,
    body: values,
  });
}

export function updateBusiness(token, msmeId, values) {
  return apiRequest(`/msmes/${msmeId}`, {
    method: "PUT",
    token,
    body: values,
  });
}

export function deactivateBusiness(token, msmeId) {
  return apiRequest(`/msmes/${msmeId}/deactivate`, {
    method: "PATCH",
    token,
  });
}

export function getRepresentatives(token) {
  return apiRequest("/users/my-msme-representatives", {
    token,
  });
}

export function getBusinessDropdown(token) {
  return apiRequest("/users/my-msmes", {
    token,
  });
}

export function createRepresentative(token, values) {
  return apiRequest("/users/msme-representatives", {
    method: "POST",
    token,
    body: values,
  });
}

export function updateRepresentative(token, userId, values) {
  return apiRequest(`/users/msme-representatives/${userId}`, {
    method: "PUT",
    token,
    body: values,
  });
}