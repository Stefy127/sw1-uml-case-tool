const origin = window.location.origin;

export const API_BASE_URL =
  window.location.hostname === 'localhost'
    ? 'http://localhost:8080/api'
    : `${window.location.origin}/api`;