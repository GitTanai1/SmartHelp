import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

import { ApiService } from '../core/api.service';
import { Category, CreateUserRequest, User } from '../core/models';

@Component({
  selector: 'app-operations',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './operations.component.html',
  styleUrl: './operations.component.scss',
})
export class OperationsComponent implements OnInit {
  users: User[] = [];
  categories: Category[] = [];
  loading = true;
  error: string | null = null;
  saving = false;
  userForm: CreateUserRequest = { name: '', email: '', role: 'CUSTOMER' };
  categoryName = '';
  editingUserId: number | null = null;
  editingCategoryId: number | null = null;

  constructor(private api: ApiService) {}

  ngOnInit(): void { this.reload(); }

  reload(): void {
    this.loading = true;
    this.error = null;
    let usersLoaded = false;
    let categoriesLoaded = false;
    const finish = () => { if (usersLoaded && categoriesLoaded) this.loading = false; };
    this.api.getUsers().subscribe({ next: users => { this.users = users; usersLoaded = true; finish(); }, error: () => this.fail('Could not load workspace members.') });
    this.api.getCategories().subscribe({ next: categories => { this.categories = categories; categoriesLoaded = true; finish(); }, error: () => this.fail('Could not load support categories.') });
  }

  saveUser(): void {
    if (!this.userForm.name.trim() || !this.userForm.email.trim()) return;
    this.saving = true;
    const request = { ...this.userForm, name: this.userForm.name.trim(), email: this.userForm.email.trim() };
    const operation = this.editingUserId === null ? this.api.createUser(request) : this.api.updateUser(this.editingUserId, request);
    operation.subscribe({ next: () => { this.resetUserForm(); this.saving = false; this.reload(); }, error: err => this.fail(err?.error?.message ?? 'Could not save member.') });
  }

  editUser(user: User): void { this.editingUserId = user.id; this.userForm = { name: user.name, email: user.email, role: user.role }; }
  resetUserForm(): void { this.editingUserId = null; this.userForm = { name: '', email: '', role: 'CUSTOMER' }; }
  deleteUser(user: User): void {
    if (!confirm(`Remove ${user.name} from the workspace?`)) return;
    this.api.deleteUser(user.id).subscribe({ next: () => this.reload(), error: err => this.fail(err?.error?.message ?? 'Could not remove member.') });
  }

  saveCategory(): void {
    const name = this.categoryName.trim();
    if (!name) return;
    this.saving = true;
    const operation = this.editingCategoryId === null ? this.api.createCategory({ name }) : this.api.updateCategory(this.editingCategoryId, { name });
    operation.subscribe({ next: () => { this.resetCategoryForm(); this.saving = false; this.reload(); }, error: err => this.fail(err?.error?.message ?? 'Could not save category.') });
  }

  editCategory(category: Category): void { this.editingCategoryId = category.id; this.categoryName = category.name; }
  resetCategoryForm(): void { this.editingCategoryId = null; this.categoryName = ''; }
  deleteCategory(category: Category): void {
    if (!confirm(`Delete the ${category.name} category? Tickets and articles using it may need reassignment.`)) return;
    this.api.deleteCategory(category.id).subscribe({ next: () => this.reload(), error: err => this.fail(err?.error?.message ?? 'Could not delete category.') });
  }

  private fail(message: string): void { this.error = message; this.loading = false; this.saving = false; }
}
