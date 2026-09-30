import { pageItems } from './pagination.js';

describe('pageItems', () => {
  it('lists every page when there are few', () => {
    expect(pageItems(0, 1)).toEqual([0]);
    expect(pageItems(1, 3)).toEqual([0, 1, 2]);
    expect(pageItems(0, 4)).toEqual([0, 1, 2, 3]);
  });
  it('puts gaps around the current page window', () => {
    expect(pageItems(5, 10)).toEqual([0, 'gap', 4, 5, 6, 'gap', 9]);
  });
  it('fills a single skipped page instead of an ellipsis', () => {
    expect(pageItems(3, 7)).toEqual([0, 1, 2, 3, 4, 5, 6]);
  });
  it('handles the edges', () => {
    expect(pageItems(0, 10)).toEqual([0, 1, 'gap', 9]);
    expect(pageItems(9, 10)).toEqual([0, 'gap', 8, 9]);
  });
  it('returns nothing for no pages', () => {
    expect(pageItems(0, 0)).toEqual([]);
  });
});
