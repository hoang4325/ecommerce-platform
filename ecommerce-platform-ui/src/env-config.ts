const API_BASE_URL = (import.meta as any).env?.VITE_API_BASE_URL || 'http://localhost:8081';
const STRIPE_PUBLISHABLE_KEY = (import.meta as any).env?.VITE_STRIPE_PUBLISHABLE_KEY || 'pk_test_51Sp6v1KAi7W8OloWy4X1iKCE8ORDVdvoenOB8KlwZUQ4rBPmAwx5Opk9lFfbJW8g1qE4hq0YcWeyjgyBo59pCK1l00fMjzcgJX';

export { API_BASE_URL, STRIPE_PUBLISHABLE_KEY };
