export const hasPermission = (perms: string[] | undefined, required?: string | string[]) => {
  if (!required) return true;
  const list = Array.isArray(required) ? required : [required];
  return !!perms && list.some((p) => perms.includes(p));
};
