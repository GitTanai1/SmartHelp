import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

/**
 * App is the root component. It renders the navigation bar and a
 * <router-outlet> where Angular swaps in whichever component matches
 * the current URL.
 *
 * The nav uses routerLink (not href) so Angular handles navigation
 * client-side without a full page reload.
 */
@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {}
