/** Field-level messages for the auth forms (only invalid fields are present). Mirrors the server rules. */
export function loginErrors({ username, password }) {
  const errors = {};
  if (!username.trim()) errors.username = 'Vui lòng nhập tên đăng nhập';
  if (!password) errors.password = 'Vui lòng nhập mật khẩu';
  return errors;
}

export function signupErrors({ username, password, lastname, firstname, dob }) {
  const errors = {};
  if (username.trim().length < 3) errors.username = 'Tên đăng nhập tối thiểu 3 ký tự';
  if (password.length < 8) errors.password = 'Mật khẩu tối thiểu 8 ký tự';
  if (!lastname.trim()) errors.lastname = 'Vui lòng nhập họ';
  if (!firstname.trim()) errors.firstname = 'Vui lòng nhập tên';
  if (!dob) errors.dob = 'Vui lòng chọn ngày sinh';
  return errors;
}
