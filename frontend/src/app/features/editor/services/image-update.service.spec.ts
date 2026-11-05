import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { API_BASE_URL } from '../../../core/config/api.config';
import { ImageUpdateService } from './image-update.service';

describe('ImageUpdateService', () => {
  let service: ImageUpdateService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [ImageUpdateService, provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(ImageUpdateService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('sends image update preview as multipart with diagram version and instruction', () => {
    const file = new File(['png'], 'diagram.png', { type: 'image/png' });
    service.preview('diagram-1', 7, file, 'agrega la clase Pedido', false).subscribe();
    const request = http.expectOne((item) => item.url === `${API_BASE_URL}/import/image/update-preview`);
    expect(request.request.method).toBe('POST');
    expect(request.request.params.get('diagramId')).toBe('diagram-1');
    expect(request.request.params.get('baseVersion')).toBe('7');
    expect(request.request.body.get('file')).toBe(file);
    expect(request.request.params.get('instruction')).toBe('agrega la clase Pedido');
    request.flush({ success: true, diagramId: 'diagram-1', baseVersion: 7, operations: [], warnings: [] });
  });

  it('posts only selected proposals and the preview base version on apply', () => {
    const operations = [{ proposal: { proposalId: 'p1', type: 'ADD_ATTRIBUTE' }, selected: true }];
    service.apply('diagram-1', 7, false, operations).subscribe();
    const request = http.expectOne(`${API_BASE_URL}/import/image/update-apply`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ diagramId: 'diagram-1', baseVersion: 7, allowDestructiveChanges: false, operations });
    request.flush({ diagramId: 'diagram-1', previousVersion: 7, newVersion: 8, canonicalModel: {}, viewState: {}, appliedOperations: 1 });
  });
});
