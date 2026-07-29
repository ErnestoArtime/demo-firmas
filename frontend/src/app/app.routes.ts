import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./home/home.component').then((m) => m.HomeComponent)
  },
  {
    path: 'drafts',
    loadComponent: () => import('./drafts/draft-list.component').then((m) => m.DraftListComponent)
  },
  {
    path: 'drafts/new',
    loadComponent: () => import('./drafts/draft-create.component').then((m) => m.DraftCreateComponent)
  },
  {
    path: 'drafts/:id',
    loadComponent: () => import('./drafts/draft-detail.component').then((m) => m.DraftDetailComponent)
  },
  {
    path: 'firmar/:token',
    loadComponent: () => import('./sign/sign-by-token.component').then((m) => m.SignByTokenComponent)
  },
  { path: '**', redirectTo: '' }
];
