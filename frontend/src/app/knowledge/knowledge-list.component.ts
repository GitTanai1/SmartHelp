//knowledge-list.component.ts

import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

import { ApiService } from '../core/api.service';
import { Category, KnowledgeArticle } from '../core/models';

@Component({
  selector: 'app-knowledge-list',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './knowledge-list.component.html',
  styleUrl: './knowledge-list.component.scss',
})
export class KnowledgeListComponent implements OnInit {
  articles: KnowledgeArticle[] = [];
  categories: Category[] = [];

  filterCategoryId: string = '';
  searchQuery: string = '';

  loading = true;
  error: string | null = null;

  // Inline article form
  showForm = false;
  editing: KnowledgeArticle | null = null;
  formModel = { categoryId: 0, title: '', content: '' };
  saving = false;
  formError: string | null = null;

  constructor(private api: ApiService) {}

  ngOnInit(): void {
    this.api.getCategories().subscribe({ next: (cats) => (this.categories = cats) });
    this.loadArticles();
  }

  loadArticles(): void {
    this.loading = true;
    this.error = null;
    this.api
      .getKnowledgeArticles({
        categoryId: this.filterCategoryId ? Number(this.filterCategoryId) : undefined,
        query: this.searchQuery || undefined,
      })
      .subscribe({
        next: (articles) => {
          this.articles = articles;
          this.loading = false;
        },
        error: () => {
          this.error = 'Could not load knowledge articles.';
          this.loading = false;
        },
      });
  }

  openCreateForm(): void {
    this.editing = null;
    this.formModel = { categoryId: this.categories[0]?.id ?? 0, title: '', content: '' };
    this.formError = null;
    this.showForm = true;
  }

  openEditForm(article: KnowledgeArticle): void {
    this.editing = article;
    this.formModel = {
      categoryId: article.categoryId,
      title: article.title,
      content: article.content,
    };
    this.formError = null;
    this.showForm = true;
  }

  closeForm(): void {
    this.showForm = false;
    this.editing = null;
    this.formError = null;
  }

  saveArticle(): void {
    if (!this.formModel.title.trim() || !this.formModel.content.trim() || !this.formModel.categoryId) {
      this.formError = 'Category, title, and content are all required.';
      return;
    }
    this.saving = true;
    this.formError = null;
    const req = {
      categoryId: Number(this.formModel.categoryId),
      title: this.formModel.title,
      content: this.formModel.content,
    };

    const op = this.editing
      ? this.api.updateKnowledgeArticle(this.editing.id, req)
      : this.api.createKnowledgeArticle(req);

    op.subscribe({
      next: () => {
        this.saving = false;
        this.closeForm();
        this.loadArticles();
      },
      error: (err) => {
        this.saving = false;
        this.formError = err?.error?.message ?? 'Failed to save article.';
      },
    });
  }

  deleteArticle(id: number): void {
    if (!confirm('Delete this knowledge article?')) return;
    this.api.deleteKnowledgeArticle(id).subscribe({
      next: () => this.loadArticles(),
      error: () => alert('Failed to delete article.'),
    });
  }

  getCategoryName(id: number): string {
    return this.categories.find((c) => c.id === id)?.name ?? String(id);
  }
}
//--------------------