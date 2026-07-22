# DESIGN SYSTEM — E-Commerce Platform UI

> **Taste Skill:** design-taste-frontend
> **Design Read:** Professional marketplace operations dashboard for B2B administrators and partners, with a clean modern data-dense language, leaning toward MUI + custom tokens + restrained presentation.
> **Dials:** DESIGN_VARIANCE: 4 | MOTION_INTENSITY: 2 | VISUAL_DENSITY: 7

---

## 1. User Groups

| Group | Role | Primary Tasks |
|-------|------|---------------|
| Customer | Browse, cart, checkout, orders | Product discovery, purchase, order tracking |
| Partner | Offers, orders, settlements, members | Product management, fulfillment, financials |
| Admin | Partners, moderation, settlements, rules | Platform governance, reconciliation |

---

## 2. Visual Direction

- Professional marketplace operations dashboard
- Clean, modern, readable
- Data-dense but not crowded
- Trust-first, no decorative elements
- Content hierarchy through spacing and typography, not cards/shadows
- Status badges use shape + color + text (not color alone)

---

## 3. Typography

| Token | Value | Usage |
|-------|-------|-------|
| `font-family` | `'Roboto', 'Helvetica', 'Arial', sans-serif` | Body text (MUI default) |
| `font-family-mono` | `'Roboto Mono', 'Consolas', monospace` | Code, IDs, amounts |
| `h1` | 1.75rem/2.25rem, 600 | Page titles |
| `h2` | 1.5rem/2rem, 600 | Section headers |
| `h3` | 1.25rem/1.75rem, 500 | Card headers |
| `h4` | 1rem/1.5rem, 500 | Group headers |
| `body1` | 0.875rem/1.5rem, 400 | Body text |
| `body2` | 0.75rem/1.25rem, 400 | Metadata |
| `caption` | 0.75rem/1rem, 400 | Labels, timestamps |
| `button` | 0.875rem/1.25rem, 500 | Buttons |
| `tabular-nums` | `font-variant-numeric: tabular-nums` | Financial columns |

---

## 4. Color Tokens

### Brand
| Token | Light | Dark | Usage |
|-------|-------|------|-------|
| `primary` | `#1976d2` | `#90caf9` | Primary actions, links |
| `primary-dark` | `#1565c0` | `#42a5f5` | Hover states |
| `primary-light` | `#42a5f5` | `#e3f2fd` | Alert backgrounds |

### Semantic
| Token | Light | Dark | Usage |
|-------|-------|------|-------|
| `success` | `#2e7d32` | `#66bb6a` | Approved, completed, active |
| `warning` | `#ed6c02` | `#ffa726` | Pending, review required |
| `error` | `#d32f2f` | `#f44336` | Rejected, failed, errors |
| `info` | `#0288d1` | `#29b6f6` | Informational |

### Neutral
| Token | Light | Dark | Usage |
|-------|-------|------|-------|
| `background` | `#f5f5f5` | `#121212` | Page background |
| `surface` | `#ffffff` | `#1e1e1e` | Cards, tables |
| `border` | `#e0e0e0` | `#333333` | Dividers, outlines |
| `text-primary` | `#212121` | `#f5f5f5` | Primary text |
| `text-secondary` | `#616161` | `#bdbdbd` | Secondary text |
| `text-disabled` | `#9e9e9e` | `#616161` | Disabled text |

### Status Colors (applied as badges)
| Status | BG Light | Text Light |
|--------|----------|------------|
| Draft | `#f5f5f5` | `#616161` |
| Pending Review | `#fff3e0` | `#e65100` |
| Changes Requested | `#fff8e1` | `#f57f17` |
| Approved | `#e8f5e9` | `#1b5e20` |
| Rejected | `#ffebee` | `#c62828` |
| Suspended | `#fce4ec` | `#880e4f` |
| Archived | `#f5f5f5` | `#424242` |
| Paid | `#e8f5e9` | `#1b5e20` |

---

## 5. Spacing Scale

| Token | Rem | px (16px base) |
|-------|-----|-----------------|
| `space-1` | 0.25rem | 4px |
| `space-2` | 0.5rem | 8px |
| `space-3` | 0.75rem | 12px |
| `space-4` | 1rem | 16px |
| `space-5` | 1.25rem | 20px |
| `space-6` | 1.5rem | 24px |
| `space-8` | 2rem | 32px |
| `space-10` | 2.5rem | 40px |
| `space-12` | 3rem | 48px |

---

## 6. Border Radius

| Token | Value | Usage |
|-------|-------|-------|
| `radius-sm` | 4px | Inputs, chips |
| `radius-md` | 6px | Buttons, cards |
| `radius-lg` | 8px | Dialogs, modals |
| `radius-xl` | 12px | Page-level containers |
| `radius-full` | 50% | Avatars, badges |

---

## 7. Shadow Tokens

| Token | Light | Usage |
|-------|-------|-------|
| `shadow-sm` | `0 1px 2px rgba(0,0,0,0.05)` | Cards (subtle) |
| `shadow-md` | `0 2px 8px rgba(0,0,0,0.08)` | Dropdowns |
| `shadow-lg` | `0 4px 16px rgba(0,0,0,0.12)` | Modals |

---

## 8. Responsive Breakpoints

| Breakpoint | Width | Target |
|------------|-------|--------|
| `xs` | 0-599px | Mobile |
| `sm` | 600-899px | Tablet |
| `md` | 900-1199px | Small desktop |
| `lg` | 1200-1535px | Desktop |
| `xl` | 1536px+ | Large desktop |

---

## 9. Component Design Rules

### 9.1 Layout (AppShell)
- Fixed left sidebar (240px on desktop, collapsed on tablet)
- Top bar with breadcrumbs, search, user menu
- Main content area with max-width 1400px
- No card-wrapping every section (use spacing + borders)

### 9.2 DataTable
- Sticky header
- Horizontal scroll on overflow
- Row hover highlight
- StatusBadge for status columns
- Action column with icon buttons
- Pagination below table (MUI TablePagination)
- Column sorting by header click
- Density: comfortable (default 52px rows)

### 9.3 Forms
- Label above input (never placeholder as label)
- Helper text below input
- Error text below input (red)
- Inline validation on blur
- Submit button disabled while submitting
- Section headers for long forms
- No more than 2 columns per row on desktop

### 9.4 StatusBadge
- Rounded chip with icon + text
- Color-coded background + text
- Shape: `radius-sm`
- Not color-only (include text label)

### 9.5 EmptyState
- Centered layout
- Optional illustration
- Title + description
- Action button when applicable
- Not a raw "No data" text

### 9.6 ConfirmDialog
- Title + message
- Cancel + Confirm buttons
- Confirm button color matches action type (error for destructive)
- Loading state while action in progress

---

## 10. Accessibility Rules

- All form inputs have associated labels
- Color is never the only indicator for status
- Focus indicators visible on all interactive elements
- Keyboard navigation supported for all actions
- ARIA labels where visual labels aren't present
- Minimum touch target 44px
- Table header sort buttons have aria-sort
- Dialog focus trapped
- Loading states announced to screen readers

---

## 11. Pattern Library

### 11.1 Page Layout Pattern
```
<AppShell>
  <PageHeader title={} breadcrumbs={} actions={} />
  <DataTable /> | <Form /> | <DetailPanel />
</AppShell>
```

### 11.2 Detail Page Pattern
```
<AppShell>
  <PageHeader title={} breadcrumbs={} />
  <Grid container spacing={3}>
    <Grid item xs={12} md={8}>
      <InfoSection />
      <Timeline />
      <AuditLog />
    </Grid>
    <Grid item xs={12} md={4}>
      <StatusCard />
      <ActionsCard />
      <MetadataCard />
    </Grid>
  </Grid>
</AppShell>
```

### 11.3 Loading State Pattern
- DataTable: SkeletonTable (rows with shimmer)
- Detail page: SkeletonText blocks
- Cards: Skeleton variants matching card shape
- Buttons: CircularProgress inside button

### 11.4 Error State Pattern
- Alert banner at top of page/component
- Retry button
- Error message (not raw exception)
- Navigation back option when applicable
