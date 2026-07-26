# Skill: Frontend Integration Guide Generator

**When to use**: You've implemented a backend feature and need to document how to integrate it in frontend

**Output**: A structured `.md` file automatically saved to the correct destination folder

**Time**: ~15 minutes (you answer questions, I generate and save the doc)

> 📐 See also [[code-best-practices]] for general naming/error-handling conventions, and [[frontend-visual-consistency]] (a router — once you know Backoffice or Portal from this skill's own first question, it points to the matching sub-file) so generated code templates match real design tokens instead of inventing new ones.

---

## How It Works

### Step 1: You Call the Skill

When you ask me to generate a frontend integration guide, I ask **2 initial questions**:

```
QUESTION 1: "Backoffice or Portal?"
   → Backoffice (Admin Dashboard)
      File will be saved to: 
      C:\Users\jlalv\Desktop\utad\projetos\Property-Management\
      management\managementfrontend\apps\backoffice\docs\integration\
   
   → Portal (Client-Facing App)
      File will be saved to:
      C:\Users\jlalv\Desktop\utad\projetos\Property Management - Client Side\
      Casa-Capital---Client-Side\docs\integration\

QUESTION 2: "Is this the feature you just developed in this chat, or an existing feature you want to document?"
   → Feature from THIS CHAT
      I extract all details automatically (endpoints, DTOs, auth, error codes)
      
   → EXISTING FEATURE
      Provide: Feature name, endpoints, DTOs, auth, error codes
```

---

### Step 2: Feature Details (Automatic or Manual)

**If it's from THIS CHAT:**
- I extract the backend implementation details
- I may ask for confirmation if needed

**If it's an EXISTING FEATURE:**
When you ask me to generate a frontend integration guide, provide:
- **Feature name** (e.g. "Lead Management")
- **New endpoints** created (GET, POST, PUT, DELETE paths)
- **Request/Response DTOs** (field names and types)
- **Error codes** added
- **Authentication/Authorization** requirements (who can call what)

Example:
```
Feature: Lead Management
Endpoints:
- GET /leads (list)
- POST /leads (create)
- PUT /leads/{id} (update)
- DELETE /leads/{id} (delete)

Main fields: name, email, phone, message, businessType
Error codes: LEAD_NOT_FOUND, LEAD_CREATE_ERROR
Auth: ADMIN can create/update/delete, EMPLOYEE can view
```

---

### Step 3: I Ask You These 5 Questions

```
[QUESTIONS FOR YOU]

1. Where should this live in the frontend?
   - Which menu section? (properties, enterprises, catalog, etc.)
   - Existing page or new page?
   - If new route segments are needed: keep them in **English**, even when nesting under an existing Portuguese top-level segment (e.g. `empreendimentos/:id/construction`, not `.../construcao`) — see [[skill-frontend-design-system]] → "Idioma dos segmentos de rota (URL)".

2. What UI components do you need?
   - List view with pagination?
   - Create/Edit modal or drawer?
   - Search/filter?
   - Detail view?
   - Bulk actions?

3. Which fields need special UI?
   - Dropdowns with options?
   - Date pickers?
   - File uploads?
   - Rich text editors?
   - Custom validations?

4. Workflow specifics
   - After create, redirect to list or stay on form?
   - Edit inline or in drawer?
   - Soft delete or hard delete?
   - Confirm before delete?

5. Any special integrations?
   - Need to link to existing entities?
   - Depend on other features?
   - Export/import data?
```

---

### Step 4: I Generate and Save the Integration Doc

Based on your answers, I'll create a `.md` file with:

```
## Generated Doc Structure

1. **Overview** — What's being added
2. **API Contract** — Endpoints, DTOs, auth requirements
3. **Component Architecture** — What to build (services, components, pages)
4. **File Structure** — Where to create files
5. **Implementation Steps** — Exact checklist to follow
6. **Code Templates** — Service, component, types, forms
7. **Integration Points** — How to wire it into existing UI (routes, menus)
8. **Error Handling** — How to handle backend errors
9. **Testing Checklist** — What to verify
```

---

## Example: What You'd Send to Frontend

Here's what the generated `.md` would look like:

```markdown
# Frontend Integration: Lead Management

## Overview
New feature to manage leads/inquiries. Users can create, view, update, delete leads.
Part of backoffice section.

## API Contract

### Endpoints
- GET /leads — List leads (paginated)
  - Query params: page, size, q (search)
  - Response: Page<LeadDTO>
  
- POST /leads — Create new lead
  - Auth: ADMIN only
  - Body: { name, email, phone, message, businessType }
  - Response: LeadDTO

- PUT /leads/{id} — Update lead
  - Auth: ADMIN only
  - Body: { name, email, phone, businessType }
  - Response: LeadDTO

- DELETE /leads/{id} — Delete lead
  - Auth: ADMIN only
  - Response: 204 No Content

### Error Codes
- LEAD_NOT_FOUND (404)
- LEAD_CREATE_ERROR (400)

## Where to Build

Location: `apps/backoffice/src/`

```
components/
├── leads/
│   ├── LeadsListPage.tsx     ← Main page
│   ├── CreateLeadDrawer.tsx  ← Create form
│   ├── LeadDetailDrawer.tsx  ← View/edit
│   └── leadsFormSchema.ts    ← Zod validation

services/
├── leadsService.ts           ← API calls

types/
├── leads.ts                  ← TypeScript interfaces

pages/
├── backoffice/leads/LeadsPage.tsx  ← Route page
```

## Implementation Steps

1. Create types: `types/leads.ts`
2. Create service: `services/leadsService.ts`
3. Create form schema: `components/leads/leadsFormSchema.ts`
4. Create components:
   - LeadsListPage.tsx
   - CreateLeadDrawer.tsx
   - LeadDetailDrawer.tsx
5. Add route in main.tsx
6. Add menu item in AppLayout.tsx
7. Test all CRUD operations

## Code Templates

[Full working code examples would go here]

## Testing Checklist

- [ ] Can list leads with pagination
- [ ] Can create new lead
- [ ] Can edit existing lead
- [ ] Can delete lead with confirmation
- [ ] Search/filter works
- [ ] Error messages show correctly
- [ ] Handles 404 when lead not found
- [ ] Authorized users can't access (if applicable)
```

---

## How to Use This

### Workflow: Feature from This Chat

**You say:**
```
"Generate frontend integration guide for the Lead Management feature we just built."
```

**I ask:**
```
Q1: Backoffice or Portal?
→ You: Backoffice

Q2: Is this the feature from this chat?
→ You: Yes
```

**I extract automatically:** Endpoints, DTOs, auth, error codes from our chat context

**I ask the 5 questions above.**

**You answer:**
```
1. New page in admin section, under "Sales" menu
2. List + create/edit drawer + detail view
3. Email should be validated, phone with pattern
4. Redirect to list after create, confirm before delete
5. No special integrations
```

**I generate the `.md` file → Automatically saved to:**
```
C:\Users\jlalv\Desktop\utad\projetos\Property-Management\
management\managementfrontend\apps\backoffice\docs\integration\
lead-management-integration.md
```

---

### Workflow: Existing Feature

**You say:**
```
"Generate frontend integration guide for Property Management feature.
Details:
- Endpoints: GET /properties, POST /properties, PUT /properties/{id}
- Main fields: address, bedrooms, price, bathrooms
- Auth: ADMIN
- Error codes: PROPERTY_NOT_FOUND, INVALID_ADDRESS"
```

**I ask:**
```
Q1: Backoffice or Portal?
→ You: Portal

Q2: Is this the feature from this chat?
→ You: No, it already exists
```

**I ask the 5 questions above.**

**You answer:**
```
1. Existing page, in "Properties" section
2. List with filters + detail view
3. Price needs currency formatting, address has autocomplete
4. Edit in modal, no delete (only admins can soft-delete)
5. Links to tenant information
```

**I generate the `.md` file → Automatically saved to:**
```
C:\Users\jlalv\Desktop\utad\projetos\Property Management - Client Side\
Casa-Capital---Client-Side\docs\integration\
property-management-integration.md
```

---

## Benefits

✅ **Auto-saves to correct folder** — No manual file management
✅ **Smart context detection** — Extracts details from this chat if applicable
✅ **Consistent format** — Always the same structure
✅ **Step-by-step** — Anyone can follow it
✅ **Copy-paste ready** — Code templates included
✅ **Reusable** — Saved in docs/ for reference

---

## The 7 Key Questions (Quick Ref)

```
INITIAL QUESTIONS:
1. Backoffice or Portal? (determines save location)
2. Feature from this chat or existing? (determines data extraction method)

IMPLEMENTATION QUESTIONS:
3. Where? (Location in app section - properties, enterprises, catalog, etc.)
4. What UI? (List/form/modal/drawer/detail)
5. Special fields? (Dropdowns/dates/uploads/validation)
6. Workflow? (On create: redirect/stay? Edit how? Delete confirm?)
7. Integrations? (Link to other features/entities)
```

Answer these and I generate and save the complete integration guide.

---

## File Locations

The skill automatically saves the generated `.md` file to the correct folder:

**Backoffice:**
```
C:\Users\jlalv\Desktop\utad\projetos\Property-Management\
management\managementfrontend\apps\backoffice\docs\integration\
{feature-name}-integration.md
```

**Portal:**
```
C:\Users\jlalv\Desktop\utad\projetos\Property Management - Client Side\
Casa-Capital---Client-Side\docs\integration\
{feature-name}-integration.md
```

---

## Related Skills

- [[code-best-practices]] — General code quality rules
- [[skill-add-backend-feature]] — How you implement the backend
- [[skill-frontend-design-system]] — Design patterns to follow in frontend
- [[frontend-visual-consistency]] — Router to verified design tokens (Backoffice and Portal)
- [[skill-add-file-upload]] — If feature involves file uploads
