---
name: Warm Harvest
colors:
  surface: '#faf9f9'
  surface-dim: '#dbdad9'
  surface-bright: '#faf9f9'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#f4f3f3'
  surface-container: '#efeded'
  surface-container-high: '#e9e8e8'
  surface-container-highest: '#e3e2e2'
  on-surface: '#1b1c1c'
  on-surface-variant: '#58423a'
  inverse-surface: '#2f3031'
  inverse-on-surface: '#f2f0f0'
  outline: '#8b7168'
  outline-variant: '#dfc0b5'
  surface-tint: '#a73a05'
  primary: '#a73a05'
  on-primary: '#ffffff'
  primary-container: '#ff7a45'
  on-primary-container: '#672000'
  inverse-primary: '#ffb59a'
  secondary: '#5d5f5f'
  on-secondary: '#ffffff'
  secondary-container: '#dcdddd'
  on-secondary-container: '#5f6161'
  tertiary: '#5f5e5e'
  on-tertiary: '#ffffff'
  tertiary-container: '#a2a1a1'
  on-tertiary-container: '#383838'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#ffdbcf'
  primary-fixed-dim: '#ffb59a'
  on-primary-fixed: '#380d00'
  on-primary-fixed-variant: '#802900'
  secondary-fixed: '#e2e2e2'
  secondary-fixed-dim: '#c6c6c7'
  on-secondary-fixed: '#1a1c1c'
  on-secondary-fixed-variant: '#454747'
  tertiary-fixed: '#e4e2e1'
  tertiary-fixed-dim: '#c8c6c6'
  on-tertiary-fixed: '#1b1c1c'
  on-tertiary-fixed-variant: '#474747'
  background: '#faf9f9'
  on-background: '#1b1c1c'
  surface-variant: '#e3e2e2'
typography:
  headline-lg:
    fontFamily: Plus Jakarta Sans
    fontSize: 24px
    fontWeight: '700'
    lineHeight: 32px
    letterSpacing: -0.02em
  headline-md:
    fontFamily: Plus Jakarta Sans
    fontSize: 20px
    fontWeight: '600'
    lineHeight: 28px
  body-md:
    fontFamily: Work Sans
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 22px
  body-sm:
    fontFamily: Work Sans
    fontSize: 12px
    fontWeight: '400'
    lineHeight: 18px
  label-bold:
    fontFamily: Work Sans
    fontSize: 14px
    fontWeight: '600'
    lineHeight: 20px
  caption:
    fontFamily: Work Sans
    fontSize: 12px
    fontWeight: '500'
    lineHeight: 16px
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  base: 8px
  xs: 4px
  sm: 8px
  md: 16px
  lg: 24px
  xl: 32px
  container-padding: 16px
  gutter: 16px
---

## Brand & Style
The design system is centered around the concept of "Steady Growth," reflecting the patient and rewarding nature of fund dividend management. It bridges the gap between professional financial utility and the emotional warmth of long-term wealth building. 

The aesthetic is **Modern Minimalist** with a focus on **Tactile Softness**. By utilizing rounded edges and a warm color palette, the UI avoids the cold, clinical feel of traditional fintech. It aims to evoke a sense of security and "quiet confidence"—the feeling of watching a well-tended garden grow. High-quality whitespace and clear visual hierarchies ensure that complex financial data remains accessible and stress-free.

## Colors
The palette is led by **Warm Orange (#FF7A45)**, a color that represents energy, harvest, and optimism. This primary hue is used for call-to-actions and key growth indicators. 

The system employs a dual-surface strategy:
- **Surface Primary:** Rice White (#F5F5F5) serves as the soft, easy-on-the-eyes background for mobile and web interfaces.
- **Surface Contrast:** Deep Charcoal (#2D2D2D) is used for headers or high-impact cards to provide a professional, "stable" anchor to the design.
- **Accents:** Success states (dividends received) use a natural green, while dividers use a subtle Light Grey (#E8E8E8) to maintain the minimalist structure without adding visual noise.

## Typography
The design system utilizes **Plus Jakarta Sans** for headings to inject a friendly, modern personality. Its rounded terminals complement the overall "Warm Harvest" aesthetic. For data-heavy views and body text, **Work Sans** is used due to its exceptional legibility and professional, grounded character.

Key hierarchy rules:
- **Primary Headers (24px):** Used for screen titles and total asset summaries.
- **Body Text (14px):** The standard for fund names, descriptions, and list items.
- **Secondary Labels (12px):** Used for metadata, dates, and helper text to maintain a clean interface.
- **Numerical Data:** Should use semi-bold weights in Work Sans to ensure financial figures are the focal point of the user's attention.

## Layout & Spacing
This design system follows a strict **8px grid system** to ensure mathematical harmony across all screens. 

- **Outer Margins:** A standard 16px horizontal margin is used for mobile containers.
- **Card Spacing:** Cards are separated by 16px vertically to allow the "Warm Orange" primary actions to breathe.
- **Internal Padding:** Most cards use a 16px internal padding, with 24px used for "Hero" cards (e.g., Total Dividends) to create a sense of importance.
- **Layout Model:** A fluid grid is preferred for mobile, while a max-width centered container (600px) is recommended for desktop views to maintain the intimate "app-like" feel.

## Elevation & Depth
Depth is achieved through **Tonal Layering** rather than aggressive shadows. 

- **Level 0 (Background):** Rice White (#F5F5F5) acts as the base canvas.
- **Level 1 (Cards):** Pure white (#FFFFFF) cards sit on top of the background. A very soft, low-opacity ambient shadow (Blur 12px, Y-offset 4px, 5% Black) is applied to give cards a subtle "lift" without looking heavy.
- **Level 2 (Active Elements):** Interactive elements or modal overlays use a slightly higher elevation or the Deep Charcoal (#2D2D2D) background to pull the user's focus forward.
- **Separation:** 1px solid lines in #E8E8E8 are used within cards to separate list items, maintaining a flat but structured feel.

## Shapes
The shape language is defined by **Soft Geometricism**. 

- **Standard Containers:** Use a 0.5rem (8px) radius to feel modern and approachable.
- **Primary Cards:** Use a 1rem (16px) radius to emphasize the "warm and safe" container metaphor.
- **Buttons:** Follow the card radius (8px) for a cohesive look, or can be fully rounded (pill-shaped) for high-priority global actions like "Reinvest."
- **Form Inputs:** Feature a subtle 8px corner radius to match the UI components, ensuring the "sharpness" of traditional finance is fully rounded off.

## Components
Consistent implementation of these components is vital for the design system's integrity:

- **Dividend Cards:** The centerpiece component. Use a white background, 16px corner radius, and 16px internal padding. Title in Headline-MD, and the dividend amount in Primary Orange (#FF7A45).
- **Primary Buttons:** Solid fill in #FF7A45 with white text. Use 16px height padding. No heavy gradients; a flat or very subtle top-light glow is preferred.
- **Progress Bars:** For "Growth Tracking," use a thick 8px track. The unfilled track should be #E8E8E8 and the filled portion should be #FF7A45.
- **Status Chips:** Small, rounded-pill tags for status (e.g., "Pending," "Distributed"). Use low-saturation background tints of the status color with high-saturation text.
- **Lists:** Clean, edge-to-edge within cards. Use the 1px #E8E8E8 divider. Ensure a minimum touch target of 44px for every list item.
- **Input Fields:** Soft grey borders (#E8E8E8) that turn #FF7A45 on focus. Labels should be in 12px Caption style.