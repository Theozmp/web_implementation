import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';
import { adminGuard } from './core/guards/admin.guard';

/**
 * Root routing table. Every feature is loaded lazily with loadComponent() so the
 * initial bundle stays small and a feature's code only downloads on first navigation.
 * authGuard/adminGuard protect the authenticated and admin routes.
 */
export const routes: Routes = [
  // default redirect: unauthenticated users land on the welcome page
  { path: '', redirectTo: 'welcome', pathMatch: 'full' },

  // Public routes (no auth required)
  {
    path: 'welcome',
    loadComponent: () =>
      import('./features/auth/welcome/welcome.component').then(m => m.WelcomeComponent),
    title: 'Welcome',
  },
  {
    path: 'login',
    loadComponent: () =>
      import('./features/auth/login/login.component').then(m => m.LoginComponent),
    title: 'Log In',
  },
  {
    path: 'register',
    loadComponent: () =>
      import('./features/auth/register/register.component').then(m => m.RegisterComponent),
    title: 'Register',
  },

  // event browsing is public; visitors can view without an account
  {
    path: 'events',
    loadComponent: () =>
      import('./features/events/events.component').then(m => m.EventsComponent),
    title: 'Browse Events',
  },
  {
    path: 'events/:id',
    loadComponent: () =>
      import('./features/events/event-detail.component').then(m => m.EventDetailComponent),
    title: 'Event Details',
  },

  // Authenticated routes
  {
    path: 'home',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/home/home.component').then(m => m.HomeComponent),
    title: 'Home',
  },
  {
    path: 'my-events',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/my-events/my-events.component').then(m => m.MyEventsComponent),
    title: 'My Events',
  },
  {
    path: 'my-events/new',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/my-events/event-form.component').then(m => m.EventFormComponent),
    title: 'New Event',
  },
  {
    path: 'my-events/:id/edit',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/my-events/event-form.component').then(m => m.EventFormComponent),
    title: 'Edit Event',
  },
  {
    path: 'my-bookings',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/my-bookings/my-bookings.component').then(m => m.MyBookingsComponent),
    title: 'My Bookings',
  },
  {
    path: 'messages',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/messages/messages.component').then(m => m.MessagesComponent),
    title: 'Messages',
  },

  // Required immediately after login when mustChangePassword == true
  {
    path: 'change-password',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/auth/change-password/change-password.component')
        .then(m => m.ChangePasswordComponent),
    title: 'Change Password',
  },

  // Admin routes
  {
    path: 'admin/users',
    canActivate: [authGuard, adminGuard],
    loadComponent: () =>
      import('./features/admin/admin-users.component').then(m => m.AdminUsersComponent),
    title: 'User Management',
  },
  {
    path: 'admin/users/:id',
    canActivate: [authGuard, adminGuard],
    loadComponent: () =>
      import('./features/admin/admin-user-detail.component').then(m => m.AdminUserDetailComponent),
    title: 'User Detail',
  },
  {
    path: 'admin/export',
    canActivate: [authGuard, adminGuard],
    loadComponent: () =>
      import('./features/admin/admin-export.component').then(m => m.AdminExportComponent),
    title: 'Export Events',
  },

  // Catch-all fallback
  { path: '**', redirectTo: 'welcome' },
];
