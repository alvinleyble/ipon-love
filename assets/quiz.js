/* Ipon, Love teaching workspace — reusable quiz widget */
class Quiz {
  constructor({ containerId, scoreId, questions }) {
    this.container = document.getElementById(containerId);
    this.scoreEl = document.getElementById(scoreId);
    this.questions = questions;
    this.score = 0;
    this.answered = 0;
    this.render();
  }

  render() {
    this.container.innerHTML = this.questions
      .map((q, i) => this._renderQuestion(q, i))
      .join('');

    this.questions.forEach((q, i) => {
      q.options.forEach(opt => {
        const btn = document.getElementById(`q${i}-opt-${opt}`);
        if (btn) btn.addEventListener('click', () => this._answer(i, opt));
      });
    });
  }

  _renderQuestion(q, i) {
    const optionBtns = q.options
      .map(opt => `<button id="q${i}-opt-${opt}" class="option-btn">${opt}</button>`)
      .join('');
    return `
      <div class="question" id="question-${i}">
        <p class="scenario">
          ${q.context ? `<span class="context">${q.context}</span>` : ''}
          ${q.scenario}
        </p>
        <div class="options">${optionBtns}</div>
        <div class="feedback" id="feedback-${i}" hidden></div>
      </div>`;
  }

  _answer(i, chosen) {
    const q = this.questions[i];
    const qEl = document.getElementById(`question-${i}`);
    const feedbackEl = document.getElementById(`feedback-${i}`);

    if (qEl.dataset.answered) return;
    qEl.dataset.answered = 'true';
    this.answered++;

    const correct = chosen === q.answer;
    if (correct) this.score++;

    q.options.forEach(opt => {
      const btn = document.getElementById(`q${i}-opt-${opt}`);
      btn.disabled = true;
      if (opt === q.answer) btn.classList.add('correct');
      else if (opt === chosen) btn.classList.add('wrong');
    });

    feedbackEl.hidden = false;
    feedbackEl.className = `feedback ${correct ? 'correct' : 'wrong'}`;
    feedbackEl.innerHTML = `<strong>${correct ? 'Correct.' : 'Not quite.'}</strong> ${q.explanation}`;

    if (this.answered === this.questions.length) this._showScore();
  }

  _showScore() {
    if (!this.scoreEl) return;
    const pct = this.score / this.questions.length;
    let label;
    if (pct === 1)    label = 'Framework fully internalised. Move to Lesson 02.';
    else if (pct >= 0.75) label = 'Strong instincts. Review the ones you missed, then move on.';
    else if (pct >= 0.5)  label = 'Getting there — re-read the framework above and retry.';
    else                  label = 'The model is still forming. Re-read, then try again.';

    this.scoreEl.hidden = false;
    this.scoreEl.innerHTML = `<strong>${this.score} / ${this.questions.length}</strong>${label}`;
  }
}
