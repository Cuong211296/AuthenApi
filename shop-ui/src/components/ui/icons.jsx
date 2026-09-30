/** Small inline SVG icon set (24px grid, 1.75 stroke, currentColor). Decorative by default. */
function Icon({ size = 20, title, children, strokeWidth = 1.75, ...rest }) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={strokeWidth}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden={title ? undefined : true}
      role={title ? 'img' : undefined}
      focusable="false"
      {...rest}
    >
      {title && <title>{title}</title>}
      {children}
    </svg>
  );
}

export const CartIcon = (p) => (
  <Icon {...p}><path d="M5 7h14l-1.2 11.1a2 2 0 0 1-2 1.9H8.2a2 2 0 0 1-2-1.9L5 7Z" /><path d="M9 10V6a3 3 0 0 1 6 0v4" /></Icon>
);
export const UserIcon = (p) => (
  <Icon {...p}><circle cx="12" cy="8" r="4" /><path d="M4 21a8 8 0 0 1 16 0" /></Icon>
);
export const SearchIcon = (p) => (
  <Icon {...p}><circle cx="11" cy="11" r="7" /><path d="m20 20-3.5-3.5" /></Icon>
);
export const CloseIcon = (p) => (
  <Icon {...p}><path d="M6 6l12 12M18 6 6 18" /></Icon>
);
export const PlusIcon = (p) => (
  <Icon {...p}><path d="M12 5v14M5 12h14" /></Icon>
);
export const MinusIcon = (p) => (
  <Icon {...p}><path d="M5 12h14" /></Icon>
);
export const CheckIcon = (p) => (
  <Icon {...p}><path d="m5 12.5 4.5 4.5L19 7.5" /></Icon>
);
export const TruckIcon = (p) => (
  <Icon {...p}><path d="M3 6h11v10H3zM14 10h4l3 3v3h-7" /><circle cx="7" cy="18" r="1.8" /><circle cx="17" cy="18" r="1.8" /></Icon>
);
export const ShieldIcon = (p) => (
  <Icon {...p}><path d="M12 3 5 6v5c0 4.5 3 8.3 7 10 4-1.7 7-5.5 7-10V6l-7-3Z" /><path d="m9 12 2 2 4-4" /></Icon>
);
export const RefreshIcon = (p) => (
  <Icon {...p}><path d="M20 11a8 8 0 0 0-14.3-4.9L4 8" /><path d="M4 4v4h4" /><path d="M4 13a8 8 0 0 0 14.3 4.9L20 16" /><path d="M20 20v-4h-4" /></Icon>
);
export const ChevronIcon = ({ direction = 'down', style, ...p }) => {
  const rotate = { down: 0, up: 180, left: 90, right: -90 }[direction] ?? 0;
  return (
    <Icon {...p} style={{ transform: `rotate(${rotate}deg)`, ...style }}><path d="m6 9 6 6 6-6" /></Icon>
  );
};
export const ArrowIcon = ({ direction = 'right', style, ...p }) => {
  const rotate = { right: 0, left: 180, up: -90, down: 90 }[direction] ?? 0;
  return (
    <Icon {...p} style={{ transform: `rotate(${rotate}deg)`, ...style }}><path d="M5 12h14M13 6l6 6-6 6" /></Icon>
  );
};
export const MenuIcon = (p) => (
  <Icon {...p}><path d="M4 7h16M4 12h16M4 17h10" /></Icon>
);
export const BagIcon = (p) => (
  <Icon {...p}><path d="M4 8h16l-1 12H5L4 8Z" /><path d="M9 8a3 3 0 0 1 6 0" /></Icon>
);
export const EyeIcon = (p) => (
  <Icon {...p}><path d="M2.5 12S6 5.5 12 5.5 21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12Z" /><circle cx="12" cy="12" r="3" /></Icon>
);
export const EyeOffIcon = (p) => (
  <Icon {...p}><path d="M3 3l18 18" /><path d="M10.6 6a10 10 0 0 1 1.4-.1c6 0 9.5 6.1 9.5 6.1a17 17 0 0 1-3.1 3.8M6.6 7.7A16.6 16.6 0 0 0 2.5 12S6 18.1 12 18.1c1.5 0 2.8-.4 4-.9" /><path d="M9.9 9.9a3 3 0 0 0 4.2 4.2" /></Icon>
);
export const CashIcon = (p) => (
  <Icon {...p}><rect x="3" y="6" width="18" height="12" rx="2.5" /><circle cx="12" cy="12" r="2.6" /><path d="M6.5 9.5v.01M17.5 14.5v.01" strokeWidth="2.4" /></Icon>
);
export const AlertIcon = (p) => (
  <Icon {...p}><path d="M12 3.5 2.8 19.5h18.4L12 3.5Z" /><path d="M12 10v4.2M12 17.2v.01" /></Icon>
);
export const ClockIcon = (p) => (
  <Icon {...p}><circle cx="12" cy="12" r="9" /><path d="M12 7v5l3 2" /></Icon>
);
export const PinIcon = (p) => (
  <Icon {...p}><path d="M12 21s7-6.2 7-11.5A7 7 0 0 0 5 9.5C5 14.8 12 21 12 21Z" /><circle cx="12" cy="9.5" r="2.5" /></Icon>
);
export const LockIcon = (p) => (
  <Icon {...p}><rect x="5" y="10.5" width="14" height="10" rx="2.5" /><path d="M8.5 10.5V8a3.5 3.5 0 0 1 7 0v2.5" /></Icon>
);
export const BoxIcon = (p) => (
  <Icon {...p}><path d="M3.5 7.5 12 3l8.5 4.5v9L12 21l-8.5-4.5v-9Z" /><path d="M3.5 7.5 12 12l8.5-4.5M12 12v9" /></Icon>
);
export const ReceiptIcon = (p) => (
  <Icon {...p}><path d="M6 3h12v18l-3-2-3 2-3-2-3 2V3Z" /><path d="M9 8h6M9 12h6" /></Icon>
);
export const StoreIcon = (p) => (
  <Icon {...p}><path d="M4 9.5 5.5 4h13L20 9.5" /><path d="M4 9.5a2.7 2.7 0 0 0 5.3 0 2.7 2.7 0 0 0 5.4 0 2.7 2.7 0 0 0 5.3 0" /><path d="M5.5 12.5V20h13v-7.5M10 20v-4.5h4V20" /></Icon>
);
export const EditIcon = (p) => (
  <Icon {...p}><path d="M4 20h4L19 9a2.8 2.8 0 0 0-4-4L4 16v4Z" /><path d="m13.5 6.5 4 4" /></Icon>
);
