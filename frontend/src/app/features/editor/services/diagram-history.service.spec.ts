import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { API_BASE_URL } from '../../../core/config/api.config';
import { DiagramHistoryService } from './diagram-history.service';

describe('DiagramHistoryService', () => {
  let service: DiagramHistoryService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [DiagramHistoryService, provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(DiagramHistoryService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('uses the paginated history, availability, undo and redo contracts', () => {
    service.list('d1', 2, 25).subscribe();
    const list = http.expectOne(request => request.url === `${API_BASE_URL}/diagrams/d1/history`);
    expect(list.request.method).toBe('GET');
    expect(list.request.params.get('page')).toBe('2');
    expect(list.request.params.get('size')).toBe('25');
    list.flush({ content: [], totalPages: 3, totalElements: 0, number: 2, size: 25, last: true });

    service.availability('d1').subscribe();
    const availability = http.expectOne(`${API_BASE_URL}/diagrams/d1/history/availability`);
    expect(availability.request.method).toBe('GET');
    availability.flush({ diagramId: 'd1', currentVersion: 4, canUndo: true, canRedo: false });

    service.undo('d1').subscribe();
    const undo = http.expectOne(`${API_BASE_URL}/diagrams/d1/history/undo`);
    expect(undo.request.method).toBe('POST');
    undo.flush({ id: 'h1' });

    service.redo('d1').subscribe();
    const redo = http.expectOne(`${API_BASE_URL}/diagrams/d1/history/redo`);
    expect(redo.request.method).toBe('POST');
    redo.flush({ id: 'h2' });
  });
});
