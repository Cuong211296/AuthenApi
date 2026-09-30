import { loginErrors, signupErrors } from './authForm.js';

const ok = { username: 'abc', password: '12345678', lastname: 'Nguyễn', firstname: 'An', dob: '2000-01-01' };

describe('signupErrors', () => {
  it('accepts a valid form', () => expect(signupErrors(ok)).toEqual({}));
  it('requires username >= 3 (after trim) and password >= 8', () => {
    expect(signupErrors({ ...ok, username: ' ab ' }).username).toMatch(/3/);
    expect(signupErrors({ ...ok, password: '1234567' }).password).toMatch(/8/);
  });
  it('requires names and date of birth', () => {
    const e = signupErrors({ ...ok, lastname: ' ', firstname: '', dob: '' });
    expect(Object.keys(e).sort()).toEqual(['dob', 'firstname', 'lastname']);
  });
});

describe('loginErrors', () => {
  it('requires both fields', () => {
    expect(Object.keys(loginErrors({ username: ' ', password: '' })).sort()).toEqual(['password', 'username']);
    expect(loginErrors({ username: 'a', password: 'b' })).toEqual({});
  });
});
